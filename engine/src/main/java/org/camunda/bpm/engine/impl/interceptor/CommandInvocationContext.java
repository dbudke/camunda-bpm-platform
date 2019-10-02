/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.interceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.ibatis.exceptions.PersistenceException;
import org.camunda.bpm.application.InvocationContext;
import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.context.ProcessDataLoggingContext;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.cmd.CommandLogger;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.context.ProcessApplicationContextUtil;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.pvm.runtime.AtomicOperation;

/**
 * In contrast to {@link CommandContext}, this context holds resources that are only valid
 * during execution of a single command (i.e. the current command or an exception that was thrown
 * during its execution).
 *
 * @author Thorben Lindhauer
 */
public class CommandInvocationContext {

  private final static CommandLogger LOG = ProcessEngineLogger.CMD_LOGGER;

  protected Throwable throwable;
  protected Command< ? > command;
  protected boolean isExecuting = false;
  protected List<AtomicOperationInvocation> queuedInvocations = new ArrayList<AtomicOperationInvocation>();
  protected BpmnStackTrace bpmnStackTrace = new BpmnStackTrace();
  protected ProcessDataLoggingContext loggingContext = new ProcessDataLoggingContext();

  public CommandInvocationContext(Command<?> command) {
    this.command = command;
  }

  public Throwable getThrowable() {
    return throwable;
  }

  public Command<?> getCommand() {
    return command;
  }

  public void trySetThrowable(Throwable t) {
    if (this.throwable == null) {
      this.throwable = t;
    }
    else {
      LOG.maskedExceptionInCommandContext(throwable);
    }
  }

  public void performOperation(AtomicOperation executionOperation, ExecutionEntity execution) {
    performOperation(executionOperation, execution, false);
  }

  public void performOperationAsync(AtomicOperation executionOperation, ExecutionEntity execution) {
    performOperation(executionOperation, execution, true);
  }

  public void performOperation(final AtomicOperation executionOperation, final ExecutionEntity execution, final boolean performAsync) {
    AtomicOperationInvocation invocation = new AtomicOperationInvocation(executionOperation, execution, performAsync);
    queuedInvocations.add(0, invocation);
    performNext();
  }

  protected void performNext() {
    AtomicOperationInvocation nextInvocation = queuedInvocations.get(0);

    if(nextInvocation.operation.isAsyncCapable() && isExecuting) {
      // will be picked up by while loop below
      return;
    }

    ProcessApplicationReference targetProcessApplication = getTargetProcessApplication(nextInvocation.execution);
    if(requiresContextSwitch(targetProcessApplication)) {

      Context.executeWithinProcessApplication(new Callable<Void>() {
        public Void call() throws Exception {
          performNext();
          return null;
        }

      }, targetProcessApplication, new InvocationContext(nextInvocation.execution));
    }
    else {
      if(!nextInvocation.operation.isAsyncCapable()) {
        // if operation is not async capable, perform right away.
        invokeNext();
      }
      else {
        try  {
          isExecuting = true;
          while (! queuedInvocations.isEmpty()) {
            // assumption: all operations are executed within the same process application...
            invokeNext();
          }
        }
        finally {
          isExecuting = false;
        }
      }
    }
  }

  protected void invokeNext() {
    AtomicOperationInvocation invocation = queuedInvocations.remove(0);
    boolean popInstance = loggingContext.pushInstanceId(invocation.getExecution().getProcessInstanceId());
    try {
      invocation.execute(bpmnStackTrace, loggingContext);
      if (popInstance) {
        loggingContext.popInstanceId();
      }
    }
    catch(RuntimeException e) {
      // log bpmn stacktrace
      bpmnStackTrace.printStackTrace(Context.getProcessEngineConfiguration().isBpmnStacktraceVerbose());
      // rethrow
      throw e;
    }
  }

  protected boolean requiresContextSwitch(ProcessApplicationReference processApplicationReference) {
    return ProcessApplicationContextUtil.requiresContextSwitch(processApplicationReference);
  }

  protected ProcessApplicationReference getTargetProcessApplication(ExecutionEntity execution) {
    return ProcessApplicationContextUtil.getTargetProcessApplication(execution);
  }

  public void rethrow() {
    if (throwable != null) {
      if (throwable instanceof Error) {
        throw (Error) throwable;
      } else if (throwable instanceof PersistenceException) {
        throw new ProcessEngineException("Process engine persistence exception", throwable);
      } else if (throwable instanceof RuntimeException) {
        throw (RuntimeException) throwable;
      } else {
        throw new ProcessEngineException("exception while executing command " + command, throwable);
      }
    }
  }

  public void updateLoggingContext() {
    loggingContext.update();
  }

  public void clearLoggingContext() {
    loggingContext.clear();
  }
}

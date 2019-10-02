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
package org.camunda.bpm.engine.test.api.context;

import java.util.List;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.bpmn.executionlistener.ExampleExecutionListenerPojo;
import org.camunda.bpm.engine.test.util.ProcessEngineLoggingRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import ch.qos.logback.classic.Level;

public class ProcessDataLoggingContextTest {

  private static final String CMD_LOGGER = "org.camunda.bpm.engine.cmd";
  private static final String PVM_LOGGER = "org.camunda.bpm.engine.pvm";
  private static final String JOB_EXECUTOR_LOGGER = "org.camunda.bpm.engine.jobexecutor";

  private IdentityService identityService;
  private RuntimeService runtimeService;
  private TaskService taskService;

  private ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  private ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);
  private ProcessEngineLoggingRule loggingRule = new ProcessEngineLoggingRule()
      .watch(JOB_EXECUTOR_LOGGER, CMD_LOGGER, PVM_LOGGER).level(Level.DEBUG);

  @Rule
  public RuleChain rules = RuleChain.outerRule(engineRule).around(testRule).around(loggingRule);

  @Before
  public void setupServices() {
    identityService = engineRule.getIdentityService();
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
  }

  @Before
  public void setUpIdentities() throws Exception {
    identityService.saveUser(identityService.newUser("fozzie"));
    identityService.saveUser(identityService.newUser("kermit"));

    identityService.saveGroup(identityService.newGroup("accountancy"));
    identityService.saveGroup(identityService.newGroup("management"));

    identityService.createMembership("fozzie", "accountancy");
    identityService.createMembership("kermit", "management");
  }

  @After
  public void tearDown() throws Exception {
    identityService.deleteUser("fozzie");
    identityService.deleteUser("kermit");
    identityService.deleteGroup("accountancy");
    identityService.deleteGroup("management");
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/usertask/UserTaskTest.testSimpleProcess.bpmn20.xml")
  public void testSimpleProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("financialReport");
    List<Task> tasks = taskService.createTaskQuery().taskCandidateUser("fozzie").list();
    Task task = tasks.get(0);
    taskService.claim(task.getId(), "fozzie");
    tasks = taskService
      .createTaskQuery()
      .taskAssignee("fozzie")
      .list();
    taskService.complete(task.getId());
    tasks = taskService.createTaskQuery().taskCandidateUser("fozzie").list();
    tasks = taskService.createTaskQuery().taskCandidateUser("kermit").list();
    taskService.complete(tasks.get(0).getId());

    testRule.assertProcessEnded(processInstance.getId());

//    loggingRule.
  }

  @Test
  @Deployment(resources = {"org/camunda/bpm/engine/test/bpmn/executionlistener/ExecutionListenersProcess.bpmn20.xml"})
  public void testExecutionListenersOnAllPossibleElements() {
    // Process start executionListener will have executionListener class that sets 2 variables
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("executionListenersProcess", "businessKey123");

    // Transition take executionListener will set 2 variables
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.complete(task.getId());

    ExampleExecutionListenerPojo myPojo = new ExampleExecutionListenerPojo();
    runtimeService.setVariable(processInstance.getId(), "myPojo", myPojo);

    task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.complete(task.getId());

    // second user task uses a method-expression as executionListener: ${myPojo.myMethod(execution.eventName)}
    task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.complete(task.getId());

    testRule.assertProcessEnded(processInstance.getId());
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/async/AsyncAfterTest.testAsyncAfterAndBeforeManualTask.bpmn20.xml")
  public void testAsyncAfterAndBeforeManualTask() {
    ProcessInstance pi = runtimeService.startProcessInstanceByKey("testManualTask");
    testRule.waitForJobExecutorToProcessAllJobs();
    testRule.assertProcessEnded(pi.getId());
  }
}

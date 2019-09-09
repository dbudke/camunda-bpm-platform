package org.camunda.bpm.engine.test.bpmn.tasklistener.util;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;

public class UpdateTaskListener implements TaskListener {

  public static int eventCounter = 0;

  @Override
  public void notify(DelegateTask delegateTask) {
    eventCounter++;
  }

  public static void reset() {
    eventCounter = 0;
  }
}

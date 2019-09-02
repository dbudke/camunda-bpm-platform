package org.camunda.bpm.engine.impl.persistence.entity;

import java.util.function.Consumer;

/**
 * Used as a stand in for the (Java 8) Consumer<T> interface.
 */
public interface TaskModifier {

  void modifyTask(TaskEntity task);

}

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
package org.camunda.bpm.engine.context;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.MDC;

/**
 * Holds the contextual process data used in logging
 */
public class ProcessDataLoggingContext {

  private static final String PROPERTY_ACTIVITY_ID = "activityId";
  private static final String PROPERTY_INSTANCE_ID = "instanceId";
  private static final List<String> PROPERTIES = Arrays.asList(PROPERTY_ACTIVITY_ID, PROPERTY_INSTANCE_ID);

  private final Map<String, Deque<String>> properties = new HashMap<String, Deque<String>>();

  public boolean pushActivityId(String activityId) {
    return addToStackAndMdc(activityId, PROPERTY_ACTIVITY_ID);
  }

  public void popActivityId() {
    removeFromStackAndUpdateMdc(PROPERTY_ACTIVITY_ID);
  }

  public boolean pushInstanceId(String instanceId) {
    return addToStackAndMdc(instanceId, PROPERTY_INSTANCE_ID);
  }

  public void popInstanceId() {
    removeFromStackAndUpdateMdc(PROPERTY_INSTANCE_ID);
  }

  public void update() {
    for (String property : PROPERTIES) {
      updateMdc(property);
    }
  }

  public void clear() {
    for (String property : PROPERTIES) {
      MDC.remove(property);
    }
  }

  protected boolean addToStackAndMdc(String value, String property) {
    Deque<String> deque = getDeque(property);
    String current = deque.peekFirst();
    if (value == null || (current != null && current.equals(value))) {
      return false;
    }
    deque.addFirst(value);
    MDC.put(property, value);
    return true;
  }

  protected void removeFromStackAndUpdateMdc(String property) {
    getDeque(property).removeFirst();
    updateMdc(property);
  }

  protected Deque<String> getDeque(String property) {
    Deque<String> deque = properties.get(property);
    if (deque == null) {
      deque = new ArrayDeque<>();
      properties.put(property, deque);
    }
    return deque;
  }

  protected void updateMdc(String property) {
    String previousId = properties.containsKey(property) ? properties.get(property).peekFirst() : null;
    if (previousId == null) {
      MDC.remove(property);
    } else {
      MDC.put(property, previousId);
    }
  }
}

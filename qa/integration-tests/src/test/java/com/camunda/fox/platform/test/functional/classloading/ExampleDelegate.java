/**
 * Copyright (C) 2011, 2012 camunda services GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.camunda.fox.platform.test.functional.classloading;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;

public class ExampleDelegate implements JavaDelegate {

  private static boolean invoked = false;

  public void execute(DelegateExecution arg0) throws Exception {
    invoked = true;
  }

  public static boolean isInvoked() {
    return invoked;
  }
  
  public static void setInvoked(boolean invoked) {
    ExampleDelegate.invoked = invoked;
  }

}
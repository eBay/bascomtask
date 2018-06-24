/************************************************************************
Copyright 2018 eBay Inc.
Author/Developer: Brendan McCarthy
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    https://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**************************************************************************/
package com.ebay.bascomtask.main;

/**
 * Wraps a call to a POJO task method.
 * @author bremccarthy
 */
public interface ITaskMethodClosure {
    
    /**
     * Returns the class name of the POJO or its overwritten name(String) value.
     * @return the class name of the POJO or another name if explicitly set on the task
     */
    String getTaskName();
    
    /**
     * Retrieves the method name.
     * @return The java method name
     */
    String getMethodName();
    
    /**
     * Method name with argument types.
     * @return the java signature with just the argument types
     */
    String getMethodFormalSignature();
    
    /**
     * Method name with argument values.
     * @return the signature with the actual arguments values
     */
    String getMethodActualSignature();
    
    /**
     * Returns the target object.
     * @return the POJO whose task method will be called.
     */
    Object getTargetPojoTask();
    
    /**
     * Returns the list of POJO arguments to be passed to the task method. This is the actual 
     * list so any modifications will be passed through to the task method.
     * @return list of bindings.
     */
    Object[] getMethodBindings();
    
    /**
     * Invokes the task method.
     * @return its return result, or 'true' if the method type is void
     */
    boolean executeTaskMethod();
    
    /**
     * Returns millisecond duration of executeTaskMethod call, valid even if exception thrown.
     * @return
     */
    long getDurationMs();
    
    /**
     * Returns nanosecond duration of executeTaskMethod call, valid even if exception thrown.
     * @return
     */
    long getDurationNs();
}

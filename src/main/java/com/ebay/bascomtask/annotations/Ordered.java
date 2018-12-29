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
package com.ebay.bascomtask.annotations;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that arguments of the same type are to be delivered in the order added to the
 * orchestrator. For a List Parameter, the list will be so ordered. For a non-list parameter 
 * with multiple input instances provided, the resulting multiple invocations will be 
 * time-ordered to preserve this sequence. The ordering applies across the set of parameters 
 * to a given method, so if there are multiple Ordered parameters to a method the ordering 
 * is depth-first across the input set. For example, for a task method defined as
 * <pre>
 *   {@literal @}Work public void exec(@Ordered A a, @Ordered B b) {...}
 * </pre>
 * and given two instances each for a and b, the ordering will be as follows:
 * <ol>
 * <li> a0,b0
 * <li> a0,b1
 * <li> a1,b0
 * <li> a1,b1
 * </ol>
 *  
 * @author brendanmccarthy
 */
@Documented
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface Ordered {
}

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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a task method that may be executed when a task is added through
 * {@link com.ebay.bascomtask.main.Orchestrator#addWork(Object)} rather 
 * than {@link com.ebay.bascomtask.main.Orchestrator#addPassThru(Object)}.
 * A {@literal @}Work method implements the primary functionality of a task.
 * @author brendanmccarthy
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Work {
	/**
	 * Normally task methods are executed in their own thread when the opportunity for
	 * parallel execution arises. Override that here to specify that task method is
	 * sufficiently fast that it would be quicker to execute directly rather than
	 * spawning it in a new thread.
	 * @return true iff the associated method is 'light'
	 */
	public boolean light() default false;
	
	/**
	 * Specifies alternative behavior when a method is invoked more than once, e.g.
	 * because there are multiple instances of one or more parameters.
	 * @return the scope for the associated method
	 */
	public Scope scope() default Scope.FREE;
}

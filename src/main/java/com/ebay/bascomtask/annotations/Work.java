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
 * Marks a method as a "task method" to be executed when a task is added by
 * calling {@link com.ebay.bascomtask.main.Orchestrator#addWork(Object)} rather
 * than {@link com.ebay.bascomtask.main.Orchestrator#addPassThru(Object)}. A
 * {@literal @}Work method implements the primary functionality of a task. It
 * will be recognized and executed as a tast method whether it is public or not.
 * <p>
 * Arguments to a task method can be any non-primitive Object, though each of
 * those objects should also have been added to the orchestrator prior to its
 * execution. Upon execution of a task method, each of its arguments will have
 * already been invoked. An exception to this is any parameter of type
 * {@link com.ebay.bascomtask.main.ITask} which are not 'executed' separately in
 * this sense, but simply injected with the <code>ITask</code> wrapper for the
 * POJO itself. A task method can also have no arguments, in which case it is
 * invoked immediately upon orchestrator execution.
 * <p>
 * A task method can return a boolean result or simply be void, which equates to
 * returning {@code true}. A return value of {@code false} indicates that
 * downstream tasks should not fire: any task fires only if all of its inputs
 * have fired, except for {@link java.util.List} parameters, which never prevent
 * firing but instead any false-returning tasks will be excluded from the list
 * (which means that a list parameter may be empty).
 * 
 * @author brendanmccarthy
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Work {
    /**
     * Normally task methods are executed in their own thread when the
     * opportunity for parallel execution arises. Override that here to specify
     * that task method is sufficiently fast that it would be quicker to execute
     * directly rather than spawning it in a new thread.
     * 
     * @return true iff the associated method is 'light'
     */
    public boolean light() default false;

    /**
     * Specifies alternative behavior when a method is invoked more than once,
     * e.g. because there are multiple instances of one or more parameters.
     * 
     * @return the scope for the associated method
     */
    public Scope scope() default Scope.FREE;
}

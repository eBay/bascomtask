/*-**********************************************************************
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
package com.ebay.bascomtask.core;

import com.ebay.bascomtask.exceptions.MisplacedTaskMethodException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Common interface that all user POJO task interfaces must implement in order to be added to an Orchestrator,
 * for example like this:
 * <pre>{@code
 * interface IDelay implements TaskInterface<IDelay> {
 *    // Add test methods here
 * }
 *
 * class Delay implements IDelay {...}
 * }</pre>
 *
 * <p>Task classes such as <code>Delay</code> or its interface <code>IDelay</code> above can implement/extend other
 * interfaces, but among all of them only one should be designated as argument to TaskInterface in the manner above.
 *
 * <p>There are no required methods to implement from this interface since all have default implementations. For
 * uncommon cases some of these methods may usefully be overridden as noted in each javadoc. Several convenience
 * methods are also provided for wrapping and unwrapping values in/from CompletableFutures. There use is completely
 * optional.
 *
 * @param <T> identifies that interface that defines task methods
 * @author Brendan McCarthy
 * @see Orchestrator#task(TaskInterface) 
 */
public interface TaskInterface<T> {

    /**
     * Provides a name for this task, useful for logging and profiling.
     *
     * TaskWrappers (as returned by {@link Orchestrator#task(TaskInterface)}, already support this method which
     * therefore works in expressions like <pre><code>$.task(myTask).name("myTask").myMethod()</code></pre>.
     * That is the typical usage, so there is normally no need to override this method, but subclasses can
     * override if desired to also enable invocation directly on the task, e.g.
     * <pre><code>$.task(myTask.name("myTask")).myMethod()</code></pre>.
     *
     * @param name to set
     * @return this
     */
    default T name(String name) {
        throw new MisplacedTaskMethodException(this,"name");
    }

    /**
     * Returns the name of this task, useful for logging and profiling. Subclasses can optionally
     * override if they want to expose something other than the class name as the default name. Whether or not
     * overridden, any TaskWrapper (the object that is returned by adding a task to an Orchestrator) that wraps
     * this object (there can be more than one) will manage its own name and use this value returned here only
     * if not explicitly set during task wiring.
     *
     * @return name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Forces execution of task methods on this object to be done inline, i.e. prevents a new thread from being
     * spawned when an <code>Orchestrator</code> might otherwise do so.
     *
     * TaskWrappers (as returned by {@link Orchestrator#task(TaskInterface)}, already support this method which
     * therefore works in expressions like <pre><code>$.task(myTask).light().myMethod()</code></pre>.
     * That is the typical usage, so there is normally no need to override this method, but subclasses can
     * override if desired to also enable invocation directly on the task, e.g.
     * <pre><code>$.task(myTask.light()).myMethod()</code></pre>.
     *
     * <p>This call reverses any previous call to {@link #runSpawned()}. The impact of this call is redundant for
     * task methods that already have {@link com.ebay.bascomtask.annotations.Light} annotation.
     *
     * @return this
     */
    default T light() {
        throw new MisplacedTaskMethodException(this,"light");
    }

    /**
     * Indicates the default weight of task methods that do not have a {@link com.ebay.bascomtask.annotations.Light}
     * annotation.
     *
     * @return true iff by default a thread should never be spawned for task methods on this class
     */
    default boolean isLight() {
        return false;
    }

    /**
     * Forces a new thread to be allocated for task methods on this class, which can be useful to keep the calling
     * thread free for other purposes. This overrides the default behavior, which is spawn threads
     * when an executing thread finds more than one task ready to fire.
     *
     * TaskWrappers (as returned by {@link Orchestrator#task(TaskInterface)}, already support this method which
     * therefore works in expressions like <pre><code>$.task(myTask).runSpawned().myMethod()</code></pre>.
     * That is the typical usage, so there is normally no need to override this method, but subclasses can
     * override if desired to also enable invocation directly on the task, e.g.
     * <pre><code>$.task(myTask.runSpawned()).myMethod()</code></pre>.
     *
     * <p>This call reverses any previous call to {@link #light()}. Its effect is superseded by a
     * {@link com.ebay.bascomtask.annotations.Light} annotation on a task method if present.
     *
     * @return this
     */
    @SuppressWarnings("unchecked")
    default T runSpawned() {
        throw new MisplacedTaskMethodException(this,"runSpawned");
    }

    /**
     * Indicates that task methods should have threads spawned for them.
     *
     * @return true iff by default a thread should be spawned for task methods on this class
     */
    default boolean isRunSpawned() {
        return false;
    }

    /**
     * Forces immediate activation of task methods instead of the default behavior which is that task methods
     * are only lazily activated according to the rules described in {@link Orchestrator#task(TaskInterface)}.
     *
     * TaskWrappers (as returned by {@link Orchestrator#task(TaskInterface)}, already support this method which
     * therefore works in expressions like <pre><code>$.task(myTask).activate(true).myMethod()</code></pre>.
     * That is the typical usage, so there is normally no need to override this method, but subclasses can
     * override if desired to also enable invocation directly on the task, e.g.
     * <pre><code>$.task(myTask.activate(true)).myMethod()</code></pre>.
     *
     * @return this
     */
    default T activate() {
        throw new MisplacedTaskMethodException(this,"activate");
    }

    /**
     * Indicates that task methods should be activated immediately. Subclasses can override to change the
     * default false return value if desired.
     *
     * @return true iff by default a thread should never be spawned for task methods on this class
     */
    default boolean isActivate() {
        return false;
    }

    /**
     * Convenience method for accessing the supplied future value that attempts to unwrap
     * {@link java.util.concurrent.CompletionException}s in a more useful than is provided
     * by calling the standard {@link CompletableFuture#join} operation.
     *
     * @param future to retrieve value from
     * @param <R>    type of value returned
     * @return value wrapped by the supplied future, which may or may not be null
     */
    default <R> R get(CompletableFuture<R> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable x = e.getCause();
            RuntimeException re;
            if (x instanceof RuntimeException) {
                re = (RuntimeException)x;
            } else {
                re = new RuntimeException("get() failed",e);
            }
            throw re;
        }
    }

    /**
     * Convenience method creates a CompletableFuture from a constant value, which may or may not be null.
     *
     * @param arg value to wrap
     * @param <R> type of value returned
     * @return CompletableFuture that wraps the provided value
     */
    default <R> CompletableFuture<R> complete(R arg) {
        return CompletableFuture.completedFuture(arg);
    }

    /**
     * Convenience method for returning a void result. The framework requires a return result
     * even for logically void methods in order to expose common methods (such as being able to start
     * task execution) on the return result.
     *
     * @return a Void result
     */
    default CompletableFuture<Void> complete() {
        return CompletableFuture.allOf();
    }
}

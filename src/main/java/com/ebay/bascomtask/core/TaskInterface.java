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
package com.ebay.bascomtask.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Common interface that all user POJO task object interfaces should implement, like this:
 * <pre>{@code
 * interface IDelay implements TaskInterface<IDelay> {
 *    public void exec(...);
 * }
 * 
 * class Delay implements IDelay {...}
 * }</pre>
 * 
 * Note that Delay or IDelay can implement/extend other interfaces, but among all of them only
 * one should be designated as a TaskInterface in the manner above.
 * 
 * There are no methods to implement from this interface, but it does provide several common
 * methods on tasks. It also allows the BT task-dependency mechanism to work properly.
 *
 * @author Brendan McCarthy
 *
 * @param <T> identifies that interface as one that defines task methods
 */
public interface TaskInterface<T> {

    /**
     * Provides a name for this task, useful for logging and profiling. Subclasses can optionally override
     * if they want to allow setting a default name. When invoked on a TaskWrapper (the object that is returned by
     * adding a task to an Orchestrator), it does not pass that call to this method. TaskWrappers manages names
     * on their own (any number of TaskWrappers can reference the same user task).
     *
     * @param name to set
     * @return this
     */
    default T name(String name) {
        throw new RuntimeException("Invalid call for setting name \"" + name + "\", subclass has not overridden this method");
    }
    
    /**
     * Returns the name of this task, useful for logging and profiling. Subclasses can optionally
     * override if they want to expose something other than the class name as the default name. Whether or not
     * overridden, any TaskWrapper (the object that is returned by adding a task to an Orchestrator) that wraps
     * this object (there can be more than one) will manage its own name and use this value returned here only
     * if not explicitly set during task orchestration.
     *
     * @return name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Forces execution within the outer thread, i.e. prevents a new thread from being spawned for this task.
     * Equivalent to using the {@link com.ebay.bascomtask.annotations.Light} annotation on a task method.
     *
     * <p>Reverses any previous call to {@link #runSpawned()}
     *
     * @return this
     */
    @SuppressWarnings("unchecked")
    default T light() {
        return (T)this;
    }

    /**
     * Forces a new thread to be allocated for this task. This can be useful to keep the calling task free
     * for other purposes. This overrides the default behavior, which is spawn threads for each task &gt; 1 when an
     * executing thread finds more than one task ready to fire.
     *
     * <p>Reverses any previous call to {@link #light()} or any {@link com.ebay.bascomtask.annotations.Light} annotation
     * @return this
     */
    @SuppressWarnings("unchecked")
    default T runSpawned() {
        return (T)this;
    }

    /**
     * Convenience method that calls future.get() but turns any checked exceptions from
     * that call into unchecked ones. This is useful in tasks because a task is always
     * called with valid futures so those exceptions will never be generated. Calling this
     * method is not a requirement as task methods can simply call future.get() directly
     * and handle or throw the exceptions themselves. 
     * @param future to retrieve value from
     * @param <R> type of value returned
     * @return value wrapped by the supplied future, which may or may not be null
     */
    default <R> R get(Future<R> future) {
        try {
            return future.get();
        }
        catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Unexpected access fault on future",e);
        }
    }

    /**
     * Convenience method creates a CompletableFuture from a constant value, which
     * may or may not be null.
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
     * @return a void result
     */
    default CompletableFuture<Void> complete() {
        return CompletableFuture.allOf();
    }
}

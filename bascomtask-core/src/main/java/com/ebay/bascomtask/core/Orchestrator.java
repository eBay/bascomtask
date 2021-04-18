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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 * Provides entry points for task method activation, and for common configuration among all such tasks. Activation
 * means that task methods are scheduled for execution which will happen once all of their CompletableFuture
 * arguments are completed.
 *
 * <p>The prototypical use of this class involves adding a POJO task class to this orchestrator, calling one of its
 * task methods, activating that method, and retrieving the result. These operations are commonly performed together
 * as in the following example:
 * <pre>
 * 1.    Orchestrator $ = Orchestrator.create();
 * 2.    CompletableFuture&lt;T&gt; future = $.task(new MyTask()).myMethod(...);
 * 3.    T value = future.get();
 * </pre>
 * The task class that is added to this Orchestrator through the call to <code>$.task()</code> at line 2 can take can
 * be any class that implements {@link TaskInterface}. A wrapper is returned that enables the subsequent call to
 * <code>myMethod</code> to have its dependencies recorded but not yet (at that point) be activated; the returned
 * CompletableFuture is not and will not be completed until activation occurs. In the example above, activation
 * occurs implicitly through the call to <code>get()</code> at line 3.
 *
 * <p>Explicit activation can also be done by calling {@link #activate(CompletableFuture)} on this class. There are
 * a number of variations of the <code>activate</code> method providing different semantics for completion guarantees
 * and return results. Explicitly calling <code>activate</code> is also the only way to activate multiple
 * CompletableFutures at once, which can be desirable for maximizing parallelism.
 *
 * <p>Exceptions thrown from task methods are propagated to the caller, regardless of thread, through the
 * CompletableFutures returned by those task methods, e.g. by calling {@link CompletableFuture#get()} or
 * {@link CompletableFuture#handle(BiFunction)}. As seen in any resulting stack trace, whatever operation that
 * initiated activation will be the starting point, even though the exception might not be visible until a later point
 * in the code. Thus one might get an exception on a call to {@code get()} that if activated separately from the
 * get will have only reflect the activating stack trace.
 *
 * <p>There is no limit on the number of orchestrators created. They are thread-safe and relatively lightweight.
 * Task activation/execution is only tied to its orchestrator for configuration purposes. Tasks from different
 * orchestrators can be freely intermixed.
 *
 * @author Brendan McCarthy
 */
public interface Orchestrator extends CommonConfig {

    /**
     * Creates an Orchestrator with no name.
     *
     * @return new Orchestrator
     */
    static Orchestrator create() {
        return create(null);
    }

    /**
     * Creates a new named Orchestrator, which is the same as <pre>create().setName(name)</pre>;
     *
     * @param name for orchestrator, used in loggin
     * @return new Orchestrator
     */
    static Orchestrator create(String name) {
        return create(name, null);
    }

    /**
     * Creates an Orchestrator with the given name and argument.
     *
     * @param name optional / possibly null for this orchestrator, useful for logging
     * @param arg  to pass to {@link GlobalOrchestratorConfig.Config#updateConfigurationOn(Orchestrator, Object)}.
     * @return new Orchestrator
     */
    static Orchestrator create(String name, Object arg) {
        return new Engine(name, arg);
    }

    /**
     * Name as set from {@link #create(String)} or {@link #setName(String)}.
     *
     * @return name
     */
    String getName();

    /**
     * Sets name that will become part of the name for any threads spawned by this Orchestrator.
     *
     * @param name to set
     */
    void setName(String name);

    /**
     * Returns details for any future previously registered with {@link #activate(CompletableFuture[])} (directly or
     * through an operation on a CompletableFuture return value).
     *
     * @param cf to map
     * @return unique TaskMeta for supplied argument or null if no match
     */
    TaskMeta getTaskMeta(CompletableFuture<?> cf);

    /**
     * Returns the number of threads that have been spawned by this Orchestrator. The result is non-deterministic
     * due the inherent timing variations across threads that may vary for no externally-visible reason.
     *
     * <p>Note that a physical thread may have been returned to the thread pool and retrieved again, but these would
     * count as separate logical threads as far as this return value is concerned.
     *
     * @return number of logically-spawned threads
     */
    int getCountOfThreadsSpawned();

    /**
     * Activates (enables for execution) the task methods behind each supplied CompletableFuture if they were
     * generated through BascomTask and if they are have not already been activated. Also for each such activation,
     * activates any task method providing a parameter to the newly-activated task method, recursively.
     *
     * <p>The actual execution of a task method will take place as soon as all of its CompletableFuture inputs (if
     * any) have completed (thus accessing a CompletableFuture inside a task method will never block). The execution
     * sequencing and thread assignment is determined automatically.
     *
     * <p>Each task method will only be activated/executed only once even if passed multiple times to this method
     * or any of its variants or access operations such as {@link CompletableFuture#get()} that implicitly call
     * this method. As compared with the latter (where a single future is activated) this method call is only needed
     * to start multiple futures at the same time, or for starting tasks whose purpose is to perform a side-effect
     * that occurs without any other call to access its a value from it, or for any other situation where an access
     * of its value is not possible nor desirable.
     *
     * <p>In contrast to {@link #activateAndWait(CompletableFuture[])}, this call does not necessarily wait for any
     * of the activated task methods to finish executing. More specifically, it does not wait on any threads that
     * might be spawned to finish. Thus calls such as {@link CompletableFuture#get()}, made after calling this
     * method, may block.
     *
     * <p>Exceptions may be thrown from activated task methods but are not immediately thrown by this call
     * even if the task method completes prior to its return. Those exceptions are instead exposed through the
     * CompletableFutures being passed as arguments.
     *
     * @param futures to activate
     */
    default void activate(CompletableFuture<?>... futures) {
        activate(0, futures);
    }

    /**
     * Variant of {@link #activate(CompletableFuture[] futures)} that accepts only a single CompletableFuture
     * argument and returns that same argument after having activated it. This is provided as a convenient way to
     * activate CompletableFutures for chaining, e.g.:
     * <pre>{@code
     *     $.task.activate(future).thenAccept(v->doSomethingWith(v));
     * }</pre>
     * @param future to activate
     * @param <T> type of argument
     * @return future, after its task method (if any) has been activated (if not already activated)
     */
    default <T> CompletableFuture<T> activate(CompletableFuture<T> future) {
        return activate(0,future);
    }

    /**
     * Variant of {@link #activate(long, CompletableFuture[])} with TimeUnit option.
     *
     * @param timeout to set
     * @param timeUnit time units to apply to timeout
     * @param futures to activate
     */
    default void activate(long timeout, TimeUnit timeUnit, CompletableFuture<?>... futures) {
        activate(timeUnit.toMillis(timeout),futures);
    }

    default <T> CompletableFuture<T> activate(long timeout, TimeUnit timeUnit, CompletableFuture<T> future) {
        return activate(timeUnit.toMillis(timeout),future);
    }

    <T> CompletableFuture<T> activate(long timeoutMs, CompletableFuture<T> future);

    /**
     * Variant of {@link #activate(CompletableFuture[])} that will timeout according to the TimeoutStrategy
     *      * in effect.
     *
     * @param timeoutMs timout in milliseconds
     * @param futures   to activate
     */
    void activate(long timeoutMs, CompletableFuture<?>... futures);

    /**
     * Use {@link #activate(CompletableFuture[])} instead.
     *
     * @param futures to activate
     */
    @Deprecated
    default void execute(CompletableFuture<?>... futures) {
        activate(futures);
    }

    /**
     * Use {@link #activate(long,CompletableFuture[])} instead.
     *
     * @param timeoutMs timout in milliseconds
     * @param futures to activate
     */
    @Deprecated
    default void execute(long timeoutMs, CompletableFuture<?>... futures) {
        activate(timeoutMs,futures);
    }

    /**
     * Use {@link #activate(long,TimeUnit,CompletableFuture[])} instead.
     *
     * @param timeout timout in milliseconds
     * @param timeUnit time units to apply to timeout
     * @param futures to activate
     */
    @Deprecated
    default void execute(long timeout, TimeUnit timeUnit, CompletableFuture<?>... futures) {
        activate(timeout,timeUnit,futures);
    }

    /**
     * Variant of {@link #activate(CompletableFuture[])} that waits for all of its arguments to complete
     * (successfully or exceptionally). After this call, any call on the supplied futures will not block since
     * they will have already completed (successfully or exceptionally).
     *
     * @param futures to activate and then wait on
     */
    default void activateAndWait(CompletableFuture<?>... futures) {
        activateAndWait(0, futures);
    }

    default <T> CompletableFuture<T> activateAndWait(CompletableFuture<T> future) {
        return activateAndWait(0,future);
    }

    /**
     * Variant of {@link #activateAndWait(long, CompletableFuture[])} with TimeUnit option.
     *
     * @param timeout to set
     * @param timeUnit time units to apply to timeout
     * @param futures to activate
     */
    default void activateAndWait(long timeout, TimeUnit timeUnit, CompletableFuture<?>... futures) {
        activateAndWait(timeUnit.toMillis(timeout),futures);
    }

    /**
     * Activates then returns its single argument. Useful for easily enabling a future for chaining.
     *
     * @param timeout to set
     * @param timeUnit time units to apply to timeout
     * @param future to activate
     * @param <T> type of argument
     * @return completed future
     */
    default <T> CompletableFuture<T> activateAndWait(long timeout, TimeUnit timeUnit, CompletableFuture<T> future) {
        return activateAndWait(timeUnit.toMillis(timeout),future);
    }

    /**
     * Variant of {@link #activateAndWait(CompletableFuture[])} that will timeout according to the TimeoutStrategy
     * in effect.
     *
     * @param timeoutMs timout in milliseconds
     * @param futures   to activate
     */
    void activateAndWait(long timeoutMs, CompletableFuture<?>... futures);

    <T> CompletableFuture<T> activateAndWait(long timeoutMs, CompletableFuture<T> future);


    /**
     * Use {@link #activateAndWait(CompletableFuture[])} instead.
     *
     * @param futures to activate
     */
    @Deprecated
    default void executeAndWait(CompletableFuture<?>... futures) {
        activateAndWait(futures);
    }

    /**
     * Use {@link #activateAndWait(long,TimeUnit,CompletableFuture[])} instead.
     *
     * @param timeout to set
     * @param timeUnit time units to apply to timeout
     * @param futures to activate
     */
    @Deprecated
    default void executeAndWait(long timeout, TimeUnit timeUnit, CompletableFuture<?>... futures) {
        activateAndWait(timeout,timeUnit,futures);
    }

    /**
     * Use {@link #activateAndWait(long,CompletableFuture[])} instead.
     *
     * @param timeoutMs timout in milliseconds
     * @param futures
     */
    @Deprecated
    default void executeAndWait(long timeoutMs, CompletableFuture<?>... futures) {
        activateAndWait(timeoutMs,futures);
    }

    /**
     * Variant of {@link #activateAndWait(CompletableFuture[])} that returns a typed result from typed input.
     *
     * @param futures to activate
     * @param <T> Type of list items
     * @return the completed input values
     */
    default <T> List<T> activateAndWait(List<CompletableFuture<T>> futures) {
        return activateAndWait(0,futures);
    }

    /**
     * Variant of {@link #activateAndWait(long timeout, List)} with TimeUnit option.
     *
     * @param timeout to set
     * @param timeUnit time units to apply to timeout
     * @param futures to activate
     * @param <T> Type of list items
     * @return the completed input values
     */
    default <T> List<T> activateAndWait(long timeout, TimeUnit timeUnit, List<CompletableFuture<T>> futures) {
        return activateAndWait(timeUnit.toMillis(timeout),futures);
    }

    /**
     * Variant of {@link #activateAndWait(long timeout, CompletableFuture[])} that returns a typed result
     * matching the supplied typed input.
     *
     * @param timeoutMs timout in milliseconds
     * @param futures to activate
     * @param <T> Type of list items
     * @return the completed input values
     */
    <T> List<T> activateAndWait(long timeoutMs, List<CompletableFuture<T>> futures);

    /**
     * Initiates asynchronous activation on the supplied futures all using spawned threads, keeping the calling thread
     * free. The returned future will be completed when all the future inputs are completed. The effect is close
     * to setting {@link SpawnMode#NEVER_MAIN} <i>and</i> invoking {@link CompletableFuture#allOf(CompletableFuture[])}
     * on the same set of futures, except that using this method does not interfere with whatever SpawnMode is
     * in effect. If, for example, {@link SpawnMode#NEVER_SPAWN} is set then calling this method will have no
     * effect since that SpawnMode forces everything to be run in the calling thread.
     *
     * @param futures to activate
     * @param <T> Type of list items
     * @return future that will be bound list of values, one for each input, iff there is at least one
     */
    default <T> CompletableFuture<List<T>> activateFuture(List<CompletableFuture<T>> futures) {
        return activateFuture(0,futures);
    }

    /**
     * Variant of {@link #activateFuture(long, List)} with TimeUnit option.
     *
     * @param timeout to set
     * @param timeUnit time units to apply to timeout
     * @param futures to activate
     * @param <T> Type of list items
     * @return future that will be bound list of values, one for each input, iff there is at least one
     */
    default <T> CompletableFuture<List<T>> activateFuture(long timeout, TimeUnit timeUnit, List<CompletableFuture<T>> futures) {
        return activateFuture(timeUnit.toMillis(timeout),futures);
    }

    /**
     * Variant of {@link #activateFuture(List)} that will timeout according to the TimeoutStrategy in effect
     *
     * @param timeoutMs timout in milliseconds
     * @param futures   to activate
     * @param <T> Type of list items
     * @return future that will be bound list of values, one for each input, iff there is at least one
     */
    <T> CompletableFuture<List<T>> activateFuture(long timeoutMs, List<CompletableFuture<T>> futures);

    /**
     * Variant of {@link #activateAsReady(long, List, TriConsumer)} without a timeout.
     *
     * @param futures to activate
     * @param completionFn that will be called as many times as there are entries in the futures list
     * @param <T> type of each element
     */
    default <T> void activateAsReady(List<CompletableFuture<T>> futures, TriConsumer<T,Throwable, Integer> completionFn) {
        activateAsReady(0,futures,completionFn);
    }

    /**
     * Variant of {@link #activateAsReady(long, List, TriConsumer)} TimeUnit option.
     *
     * @param timeout to set
     * @param timeUnit time units to apply to timeout
     * @param futures to activate
     * @param completionFn that will be called as many times as there are entries in the futures list
     * @param <T> type of each element
     */
    default <T> void activateAsReady(long timeout, TimeUnit timeUnit, List<CompletableFuture<T>> futures, TriConsumer<T,Throwable, Integer> completionFn) {
        activateAsReady(timeUnit.toMillis(timeout),futures,completionFn);
    }

    /**
     * Activate each supplied future and invoke the supplied completionFn callback with each result as soon as each
     * result becomes individually available. The callback contains either a valid result or an exception as the
     * first and second arguments respectively, and in either case also includes as a third argument the ordinal
     * finishing position. Recipients can recognize when the result is complete by checking for a
     * zero value from that third argument.
     *
     * <p>Completion callbacks may be done from separate threads, but synchronization is employed so that only one
     * thread at a time is allowed to make a completion call. The calling thread does not itself wait and is
     * therefore never one to make a completion call.
     *
     * @param timeoutMs timout in milliseconds
     * @param futures to activate
     * @param completionFn that will be called as many times as there are entries in the futures list
     * @param <T> of each each element
     */
    <T> void activateAsReady(long timeoutMs, List<CompletableFuture<T>> futures, TriConsumer<T,Throwable,Integer> completionFn);

    /**
     * Ties the 'fates' of the supplied CompletableFutures together, which means that as soon as there is a fault on
     * any one of them, back-pressure is applied to prevent any of the remaining tasks or their predecessors (as
     * determined recursively) from starting if they have not already been started, and forcing a
     * {@link com.ebay.bascomtask.exceptions.TaskNotStartedException} on any CompletableFuture that was prevented
     * from starting in that manner.
     *
     * <p>A return result of 'true' can be used to initiate compensating actions on any task that previously
     * completed. Any such action can know whether any previous CompletableFuture was completed or not by calling
     * {@link CompletableFuture#isCompletedExceptionally()}.
     *
     * <p>Note that this method is only activated if the return value is activated.
     *
     * @param cfs to tie together
     * @return true if at least one of the supplied arguments generated an exception
     */
    CompletableFuture<Boolean> fate(CompletableFuture<?>... cfs);

    /**
     * Activate the supplied CompletableFuture if the supplied condition evaluates to true. The CompletableFuture
     * is not activated until the supplied condition completes, and that supplied condition is only activated if the
     * output from this method is activated.
     *
     * @param condition  to first evaluate
     * @param thenFuture to activate if condition evaluates to true
     * @param <R>        type of nested return value
     * @return thenFuture optional which only has value if condition evaluates to true
     */
    default <R> CompletableFuture<Optional<R>> cond(CompletableFuture<Boolean> condition,
                                                    CompletableFuture<R> thenFuture) {
        return cond(condition, thenFuture, false);
    }

    /**
     * Variant of {@link #cond(CompletableFuture, CompletableFuture)} with an additional boolean argument
     * indicating whether to proactively activate thenFuture at the same time as condition.
     * Activating thenFuture in that way may be wasteful since condition may eventually evaluate to false,
     * but the overall result will be faster when condition evaluates to true.
     *
     * <p>Note that this method will not be activated until an access operation is performed on the
     * return value, even though it is a void result.
     *
     * @param condition    to first evaluate
     * @param thenFuture   to activate if condition evaluates to true
     * @param thenActivate iff true then activate at same time as condition
     * @param <R>          type of nested return value
     * @return thenFuture optional which only has value if condition evaluates to true
     */
    <R> CompletableFuture<Optional<R>> cond(CompletableFuture<Boolean> condition,
                                            CompletableFuture<R> thenFuture, boolean thenActivate);

    /**
     * Activate one of two choices depending on the result of the supplied condition. Neither choice is
     * activated until condition completes, and condition is only activated if the output from this method
     * is activated.
     *
     * @param condition  to first evaluate
     * @param thenFuture chosen if condition evaluates to true
     * @param elseFuture chosen if condition evaluates to false
     * @param <R>        type of return result
     * @return thenFuture or elseFuture
     */
    default <R> CompletableFuture<R> cond(CompletableFuture<Boolean> condition,
                                          CompletableFuture<R> thenFuture,
                                          CompletableFuture<R> elseFuture) {
        return cond(condition, thenFuture, false, elseFuture, false);
    }

    /**
     * Variant of {@link #cond(CompletableFuture, CompletableFuture, CompletableFuture)} with additional boolean
     * arguments indicating whether to proactively activate thenFuture and/or elseFuture when
     * condition is activated. Activating either of those futures in that way may be wasteful since the eventual
     * condition result may choose the alternate, but the overall result will be faster when condition chooses
     * a proactively activated choice.
     *
     * @param condition    to first evaluate
     * @param thenFuture   chosen if condition evaluates to true
     * @param thenActivate iff true then activate thenFuture at same time as condition
     * @param elseFuture   chosen if condition evaluates to false
     * @param elseActivate iff true then activate elseFuture at same time as condition
     * @param <R>          type of return result
     * @return thenFuture or elseFuture result
     */
    <R> CompletableFuture<R> cond(CompletableFuture<Boolean> condition,
                                  CompletableFuture<R> thenFuture, boolean thenActivate,
                                  CompletableFuture<R> elseFuture, boolean elseActivate);

    /**
     * Creates a task wrapper around any POJO class whose interface X in turn implements TaskInterface&lt;X&gt;.
     * The returned wrapper implements the same interface <code>X</code> that intercepts all calls on it in order
     * to provide alternate execution behavior where CompletableFutures are present as inputs or output:
     * <ul>
     *     <li>If it returns a CompletableFuture, the task method is suspended until it is activated (enabled for execution)
     *     <li>Otherwise, or once activated, the task method is delayed until all CompletableFuture inputs have completed
     * </ul>
     * Activation of a task method occurs the first time any of the following actions are performed on its
     * returned CompletableFuture:
     * <ul>
     *     <li>Passing it to one of several variations of activate() defined on Orchestrator or TaskInterface
     *     <li>Accessing its value through get(), getNow(), or join() which implicitly call the above
     *     <li>Being passed as input to another task method that is activated
     * </ul>
     *
     * <p>Activating a task method implicitly activates all tasks methods supplying CompletableFutures as inputs.
     * Once activated, a task method will be executed, possibly in a different thread, as soon as all of its
     * CompletableFuture input arguments (if any) have completed. A task method with no CompletableFuture arguments
     * (perhaps no arguments at all) is executed right away, though (again) possibly in a different thread.
     *
     * <p>Internally, task method suspension (as described above) involves recording dependency links between the
     * task method and its arguments for later execution if/when activated. Those dependency links are designed to
     * have minimal performance cost so it is generally safe to build large dependency graphs in while later
     * incrementally choosing which elements are actually needed. Such graphs need not be defined all at once, it
     * can be extended at any time or place by calling this or similar methods on this class, including from
     * within other task methods, regardless of the activation state of existing task methods or the completion
     * state of any CompletableFutures.
     *
     * <p>The userTask argument can be wrapped by this call any number of times. There is in effect a many-to-
     * one relationship between these wrappers and any target user POJO instance. It may be desirable to share
     * stateful POJO instances or conversely to simply reuse stateless POJO instances to avoid the overhead of
     * creating multiple POJO instances.
     *
     * @param userTask any userTask with an interface that extends {@link TaskInterface}
     * @param <BASE>   the interface for the task
     * @param <SUB>    the implementing class type
     * @return a wrapper around the supplied userTask with the properties described above
     */
    <BASE, SUB extends TaskInterface<BASE>> BASE task(SUB userTask);

    /**
     * Creates a task wrapper on any user pojo task method. This is useful when having POJOs extend TaskInterface and/or
     * return CompletableFutures is not an option or is not otherwise desirable. The functional effect is the same as
     * if the user pojo had done those things, although features like logging/tracing cannot be as precise because
     * the actual method invocation is done inside a lambda and is not visible to the framework. An example
     * using this method would be something like:
     * <pre>{@code
     * CompletableFuture&lt;String&gt; cf = $.task(new Pojo(),p->p.anyMethod());
     * return cf.get();
     * </pre>
     * This is a modest improvement over simply using the function-task alternatives defined below:
     * <pre>
     * CompletableFuture&lt;String&gt; cf = $.fn(new Pojo().anyMethod());
     * return cf.get();
     * }</pre>
     *
     * @param userTask pojo task
     * @param fn       function to apply to that pojo if/when task is activated
     * @param <TASK>   pojo's class
     * @param <R>      type of return result
     * @return CompletableFuture for the return result
     */
    default <TASK, R> CompletableFuture<R> task(TASK userTask, Function<TASK, R> fn) {
        return fnTask(() -> userTask, fn).apply();
    }

    /**
     * Adds a task wrapper on any user pojo task method with a void result.
     *
     * @param userTask pojo task
     * @param fn       Consumer function (returns no value)
     * @param <TASK>   type of user task
     * @return CompletableFuture for void result -- activates only but doesn't return a value
     */
    default <TASK> CompletableFuture<Void> voidTask(TASK userTask, Consumer<TASK> fn) {
        return vfnTask(() -> userTask, fn).apply();
    }

    //////////////////////////////////////////////
    // Function tasks turn lambdas into tasks. ///
    //////////////////////////////////////////////

    /**
     * Produces function task that takes no arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param fn  function to apply to that pojo if/when task is activated
     * @param <R> type of return result
     * @return Function task
     */
    <R> SupplierTask<R> fnTask(Supplier<R> fn);

    /**
     * Produces function task value that takes no arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param fn  function to apply to that pojo if/when task is activated
     * @param <R> type of return result
     * @return Function task
     */
    default <R> CompletableFuture<R> fn(Supplier<R> fn) {
        return fnTask(fn).apply();
    }

    /**
     * Produces function task that takes one argument and produces one result.
     * Method invocation is light by default.
     *
     * @param s1   Supplier function (returns a value)
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> type of input
     * @param <R>  type of return result
     * @return Function task
     */
    <IN, R> SupplierTask<R> fnTask(Supplier<IN> s1, Function<IN, R> fn);

    /**
     * Produces function task value that takes one argument and produces one result.
     * Method invocation is light by default.
     *
     * @param s1   Supplier function (returns a value)
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> type of input
     * @param <R>  type of return result
     * @return Function task
     */
    default <IN, R> CompletableFuture<R> fn(Supplier<IN> s1, Function<IN, R> fn) {
        return fnTask(s1, fn).apply();
    }

    /**
     * Produces function task that takes one argument.
     * Method invocation is light by default.
     *
     * @param input input to function
     * @param fn  function to apply to that pojo if/when task is activated
     * @param <T> unwrapped type of input
     * @param <R> unwrapped type of type of function result
     * @return Function task
     */
    <T, R> SupplierTask<R> fnTask(CompletableFuture<T> input, Function<T, R> fn);

    /**
     * Produces function result from one argument.
     * Method invocation is light by default.
     *
     * @param input input to function
     * @param fn  function to apply to input when activated
     * @param <T> unwrapped type of input
     * @param <R> unwrapped type of function result
     * @return Wrapped result of calling function
     */
    default <T, R> CompletableFuture<R> fn(CompletableFuture<T> input, Function<T, R> fn) {
        return fnTask(input, fn).apply();
    }

    /**
     * Produces function task that takes two arguments.
     * Method invocation is light by default.
     *
     * @param firstInput   first input
     * @param secondInput  second input
     * @param fn function to apply to inputs when activated
     * @param <T> unwrapped base type of first input
     * @param <U> unwrapped base type of second input
     * @param <R> unwrapped type of function result
     * @return Function task
     */
    <T,U,R> SupplierTask<R> fnTask(CompletableFuture<T> firstInput, CompletableFuture<U> secondInput, BiFunction<T, U, R> fn);


    /**
     * Produces function result from two arguments.
     * Method invocation is light by default.
     *
     * @param firstInput   first input
     * @param secondInput  second input
     * @param fn function to apply to inputs when activated
     * @param <T> unwrapped base type of first input
     * @param <U> unwrapped base type of second input
     * @param <R> unwrapped type of function result
     * @return Wrapped result of calling function
     */
    default <T, U, R> CompletableFuture<R> fn(CompletableFuture<T> firstInput, CompletableFuture<U> secondInput, BiFunction<T, U, R> fn) {
        return fnTask(firstInput, secondInput, fn).apply();
    }

    /**
     * Produces function task that takes mixed arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param s1    provides first value for fn
     * @param in2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> base type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> SupplierTask<R> fnTask(Supplier<IN1> s1, CompletableFuture<IN2> in2, BiFunction<IN1, IN2, R> fn) {
        CompletableFuture<IN1> in1 = fnTask(s1).apply();
        return fnTask(in1, in2, fn);
    }

    /**
     * Produces function task value that takes mixed arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param s1    provides first value for fn
     * @param in2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> base type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> CompletableFuture<R> fn(Supplier<IN1> s1, CompletableFuture<IN2> in2, BiFunction<IN1, IN2, R> fn) {
        return fnTask(s1, in2, fn).apply();
    }

    /**
     * Produces function task that takes mixed arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param in1   provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> SupplierTask<R> fnTask(CompletableFuture<IN1> in1, Supplier<IN2> s2, BiFunction<IN1, IN2, R> fn) {
        CompletableFuture<IN2> in2 = fnTask(s2).apply();
        return fnTask(in1, in2, fn);
    }

    /**
     * Produces function task value that takes mixed arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param in1   provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> CompletableFuture<R> fn(CompletableFuture<IN1> in1, Supplier<IN2> s2, BiFunction<IN1, IN2, R> fn) {
        return fnTask(in1, s2, fn).apply();
    }

    /**
     * Produces function task that takes two lambda arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param s1    provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> SupplierTask<R> fnTask(Supplier<IN1> s1, Supplier<IN2> s2, BiFunction<IN1, IN2, R> fn) {
        CompletableFuture<IN1> in1 = fnTask(s1).apply();
        CompletableFuture<IN2> in2 = fnTask(s2).apply();
        return fnTask(in1, in2, fn);
    }

    /**
     * Produces function task value that takes two lambda arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param s1    provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> CompletableFuture<R> fn(Supplier<IN1> s1, Supplier<IN2> s2, BiFunction<IN1, IN2, R> fn) {
        return fnTask(s1, s2, fn).apply();
    }

    /**
     * Produces function task that takes one lambda argument and produces no result.
     * Method invocation is light by default.
     *
     * @param s1   provides value for fn
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> type of input1
     * @return Function task
     */
    default <IN> ConsumerTask vfnTask(Supplier<IN> s1, Consumer<IN> fn) {
        CompletableFuture<IN> in1 = fnTask(s1).apply();
        return task(new ConsumerTask.ConsumerTask1<>(in1, fn));
    }

    /**
     * Produces function task value that takes one lambda argument and produces no result.
     * Method invocation is light by default.
     *
     * @param s1   provides value for fn
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> type of input1
     * @return Function task
     */
    default <IN> CompletableFuture<Void> vfn(Supplier<IN> s1, Consumer<IN> fn) {
        return vfnTask(s1, fn).apply();
    }

    /**
     * Produces function task that takes mixed arguments and produces no result.
     * Method invocation is light by default.
     *
     * @param cf1   provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> type of input2
     * @return Function task
     */
    default <IN1, IN2> ConsumerTask vfnTask(CompletableFuture<IN1> cf1, Supplier<IN2> s2, BiConsumer<IN1, IN2> fn) {
        CompletableFuture<IN2> in2 = fnTask(s2).apply();
        return task(new ConsumerTask.ConsumerTask2<>(cf1, in2, fn));
    }

    /**
     * Produces function task value that takes mixed arguments and produces no result.
     * Method invocation is light by default.
     *
     * @param cf1   provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> type of input2
     * @return Function task
     */
    default <IN1, IN2> CompletableFuture<Void> vfn(CompletableFuture<IN1> cf1, Supplier<IN2> s2, BiConsumer<IN1, IN2> fn) {
        return vfnTask(cf1, s2, fn).apply();
    }

    /**
     * Produces function task that takes non-lambda arguments and produces no result.
     * Method invocation is light by default.
     *
     * @param cf1   provides first value for fn
     * @param cf2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> base type of input2
     * @return Function task
     */
    default <IN1, IN2> ConsumerTask vfnTask(CompletableFuture<IN1> cf1, CompletableFuture<IN2> cf2, BiConsumer<IN1, IN2> fn) {
        return task(new ConsumerTask.ConsumerTask2<>(cf1, cf2, fn));
    }

    /**
     * Produces function task value that takes non-lambda arguments and produces no result.
     * Method invocation is light by default.
     *
     * @param cf1   provides first value for fn
     * @param cf2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> base type of input2
     * @return Function task
     */
    default <IN1, IN2> CompletableFuture<Void> vfn(CompletableFuture<IN1> cf1, CompletableFuture<IN2> cf2, BiConsumer<IN1, IN2> fn) {
        return vfnTask(cf1, cf2, fn).apply();
    }

    /**
     * Produces function task that takes mixed arguments and produces no result.
     * Method invocation is light by default.
     *
     * @param s1    provides first value for fn
     * @param cf2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> base type of input2
     * @return Function task
     */
    default <IN1, IN2> ConsumerTask vfnTask(Supplier<IN1> s1, CompletableFuture<IN2> cf2, BiConsumer<IN1, IN2> fn) {
        CompletableFuture<IN1> in1 = fnTask(s1).apply();
        return task(new ConsumerTask.ConsumerTask2<>(in1, cf2, fn));
    }

    /**
     * Produces function task value that takes mixed arguments and produces no result.
     * Method invocation is light by default.
     *
     * @param s1    provides first value for fn
     * @param cf2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> base type of input2
     * @return Function task
     */
    default <IN1, IN2> CompletableFuture<Void> vfn(Supplier<IN1> s1, CompletableFuture<IN2> cf2, BiConsumer<IN1, IN2> fn) {
        return vfnTask(s1, cf2, fn).apply();
    }

    /**
     * Produces function task that takes two lambda arguments and produces no result.
     * Method invocation is light by default.
     *
     * @param s1    provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> type of input2
     * @return Function task
     */
    default <IN1, IN2> ConsumerTask vfnTask(Supplier<IN1> s1, Supplier<IN2> s2, BiConsumer<IN1, IN2> fn) {
        CompletableFuture<IN1> in1 = fnTask(s1).apply();
        CompletableFuture<IN2> in2 = fnTask(s2).apply();
        return task(new ConsumerTask.ConsumerTask2<>(in1, in2, fn));
    }

    /**
     * Produces function task value that takes two lambda arguments and produces no result.
     * Method invocation is light by default.
     *
     * @param s1    provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> type of input2
     * @return Function task
     */
    default <IN1, IN2> CompletableFuture<Void> vfn(Supplier<IN1> s1, Supplier<IN2> s2, BiConsumer<IN1, IN2> fn) {
        return vfnTask(s1, s2, fn).apply();
    }
}

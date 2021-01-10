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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/**
 * Manages execution flow among one or more tasks.
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
     * Returns details for any future previously registered with {@link #execute(CompletionStage[])} (directly or
     * through an operation on a CompletableFuture return value).
     *
     * @param cf to map
     * @return unique TaskMeta for supplied argument or null if no match
     */
    TaskMeta getTaskMeta(CompletableFuture<?> cf);

    /**
     * Returns the number of threads that have been spawned by this Orchestrator, which is a non-deterministic result
     * since the inherent timing variations across threads may result in different spawning decisions on different
     * runs of the same execution request.
     *
     * <p>Note that a physical thread may have been returned to the thread pool and retrieved again, but these would
     * count as separate logical threads as far as this return value is concerned.
     *
     * @return number of logically-spawned threads
     */
    int getCountOfThreadsSpawned();

    /**
     * Initiates execution of the task methods behind each supplied CompletableFuture if they are not already started,
     * as well as the task methods needed to supply its inputs, recursively. The dependency ordering is automatically
     * determined and strictly maintained such that ech task method is only executed when each of its arguments is
     * either not a CompletableFuture or if so then it is completed so a call to get its value will not block.
     *
     * <p>Each task method will only be executed once even it is passed multiple times to this method. As this method
     * is called implicitly for any operation (such as {@link CompletableFuture#get()} on a single future returned
     * from a task, this method call is only needed to start multiple futures at the same time, or for starting tasks
     * whose purpose is to a side-effect that occurs without any call to get a value from it, or for any other
     * situation where an access of its value is not possible nor desirable.
     *
     * <p>This call only starts ('activates') but does not necessarily wait for any of the started tasks to complete.
     * More specifically, it does not wait on any threads that are spawned to complete. Access a value on any
     * BT-managed CompletableFuture or call {@link #executeAndWait(CompletableFuture[])} to force waiting.
     *
     * @param futures to execute
     */
    default void execute(CompletionStage<?>... futures) {
        execute(0, futures);
    }

    /**
     * Variant of {@link #execute(long, CompletionStage[])} with TimeUnit option.
     *
     * @param timeout to set
     * @param timeUnit units
     * @param futures to execut
     */
    default void execute(long timeout, TimeUnit timeUnit, CompletionStage<?>... futures) {
        execute(timeUnit.toMillis(timeout),futures);
    }

    /**
     * Like {@link #execute(CompletionStage[])}, but establishes a timeout which will affect execution
     * as defined by the TimeoutStrategy in effect.
     *
     * @param timeoutMs timout in milliseconds
     * @param futures   to execute
     */
    void execute(long timeoutMs, CompletionStage<?>... futures);

    /**
     * Variant of {@link #execute(CompletionStage[])} that waits for all of its arguments to complete,
     * ignoring any exceptions. After this call, any call on the supplied futures will not block since
     * they have already completed (successfully or exceptionally).
     *
     * @param futures to execute and then wait on
     */
    default void executeAndWait(CompletableFuture<?>... futures) {
        executeAndWait(0, futures);
    }

    /**
     * Variant of {@link #executeAndWait(long, CompletableFuture[])} with TimeUnit option.
     *
     * @param timeout to set
     * @param timeUnit units
     * @param futures to execut
     */
    default void executeAndWait(long timeout, TimeUnit timeUnit, CompletableFuture<?>... futures) {
        executeAndWait(timeUnit.toMillis(timeout),futures);
    }

    /**
     * Variant of {@link #executeAndWait(CompletableFuture[])}, but establishes a timeout which will affect
     * execution as defined by the TimeoutStrategy in effect.
     *
     * @param timeoutMs timout in milliseconds
     * @param futures   to execute
     */
    void executeAndWait(long timeoutMs, CompletableFuture<?>... futures);

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
     * Execute the supplied CompletableFuture depending on the supplied condition. The CompletableFuture is not
     * executed until the supplied condition completes, and that supplied condition is only executed if the output from
     * this method is activated (reachable from a required CompletableFuture).
     *
     * @param condition  to first evaluate
     * @param thenFuture to execute if condition evaluates to true
     * @return thenFuture or elseFuture
     */
    default CompletableFuture<Void> cond(CompletableFuture<Boolean> condition,
                                         CompletableFuture<Void> thenFuture) {
        return cond(condition, thenFuture, false);
    }

    /**
     * Variant of {@link #cond(CompletableFuture, CompletableFuture)} with an additional boolean argument
     * indicating whether to proactively start executing thenFuture at the same time as condition.
     * Executing thenFuture in that way may be wasteful since condition may eventually evaluate to false,
     * but the overall result will be faster when condition evaluates to true.
     *
     * <p>Note that this method will not be activated until an access operation is performed on the
     * return value, even though it is a void result.
     *
     * @param condition    to first evaluate
     * @param thenFuture   to execute if condition evaluates to true
     * @param thenActivate iff true then start executing at same time as condition
     * @return thenFuture or elseFuture
     */
    CompletableFuture<Void> cond(CompletableFuture<Boolean> condition,
                                 CompletableFuture<Void> thenFuture, boolean thenActivate);

    /**
     * Execute one of two choices depending on the result of the supplied condition. Neither choice is
     * executed until condition completes, and condition is only executed if the output from this method
     * is activated (reachable from a required CompletableFuture).
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
     * arguments indicating whether to proactively start executing thenFuture and/or elseFuture when
     * condition is activated. Executing either of those futures in that way may be wasteful since the eventual
     * condition result may choose the alternate, but the overall result will be faster when condition chooses
     * a proactively activated choice.
     *
     * @param condition    to first evaluate
     * @param thenFuture   chosen if condition evaluates to true
     * @param thenActivate iff true then start executing at same time as condition
     * @param elseFuture   chosen if condition evaluates to false
     * @param elseActivate iff true then start executing at same time as condition
     * @param <R>          type of return result
     * @return thenFuture or elseFuture
     */
    <R> CompletableFuture<R> cond(CompletableFuture<Boolean> condition,
                                  CompletableFuture<R> thenFuture, boolean thenActivate,
                                  CompletableFuture<R> elseFuture, boolean elseActivate);

    /**
     * Creates a task wrapper around any user POJO with the requirement that that POJO implements an interface X
     * that in turn implements TaskInterface&lt;X&gt;.The result is a wrapper object that has the same signature
     * as its pojo argument, such that any CompletableFuture-returning task methods invoked on this wrapper are not
     * executed right away --they will be executed if/when any read operation on the returned CompletableFuture is
     * performed or by passing that CompletableFuture to {@link #execute(CompletionStage[])} or any of its variants.
     * Because of that lazy evaluation, tasks can be added with little performance penalty while only later choosing
     * which ones are actually needed.
     *
     * <p>The userTask argument can be freely wrapped any number of times by calling this method (as well as similar
     * methods). In other words, there is a many-to-one relationship between these wrappers and the target user POJO,
     * which may be of interest for stateful user POJO tasks or simply for avoiding the overhead of repeatedly
     * creating user task instances. In the following example, 4 task wrappers around the same user POJO task
     * instance are created:
     * <pre>
     *     MyTask myTask = new MyTask();
     *     CompletableFuture f1 = $.task(myTask).doSomething();
     *     CompletableFuture f2 = $.task(myTask).doSomething();  // or doSomethingElse()
     *     MyTask wrapper = $.task(myTask);
     *     CompletableFuture f3 = wrapper.doSomething();
     *     CompletableFuture f4 = wrapper.doSomething();  // or doSomethingElse()
     * </pre>
     *
     * <p>Note that a 'read operation' in this context refers to standard CompletableFuture access operations that
     * initiate execution. This includes simple operations such as {@link CompletableFuture#get()} as well as any
     * composition operations such as {@link CompletableFuture#thenApply(Function)} with the exception of the
     * 'compose' variations such as {@link CompletableFuture#thenCompose(Function)} whose _fn_ argument can only be
     * started with {@link #execute(CompletionStage[])}.
     *
     * <p>If a task method returns anything other than a CompletableFuture, it is executed right away, with
     * any predecessors executed in the same thread-spawning manner as occurs when CompletableFutures are activated.
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
     * @return CompletableFuture for void result -- doesn't return a value but can be used to initiate execution
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
    default <R> SupplierTask<R> fnTask(Supplier<R> fn) {
        return task(new SupplierTask.SupplierTask0<>(fn));
    }

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
    default <IN, R> SupplierTask<R> fnTask(Supplier<IN> s1, Function<IN, R> fn) {
        CompletableFuture<IN> in1 = fnTask(s1).apply();
        return task(new SupplierTask.SupplierTask1<>(in1, fn));
    }

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
     * Produces function task that takes one non-lambda argument and produces one result.
     * Method invocation is light by default.
     *
     * @param in   provides result to be applied to function
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> base type of input
     * @param <R>  type of return result
     * @return Function task
     */
    default <IN, R> SupplierTask<R> fnTask(CompletableFuture<IN> in, Function<IN, R> fn) {
        return task(new SupplierTask.SupplierTask1<>(in, fn));
    }

    /**
     * Produces function task value that takes one non-lambda argument and produces one result.
     * Method invocation is light by default.
     *
     * @param in   provides result to be applied to function
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> base type of input
     * @param <R>  type of return result
     * @return Function task
     */
    default <IN, R> CompletableFuture<R> fn(CompletableFuture<IN> in, Function<IN, R> fn) {
        return fnTask(in, fn).apply();
    }

    /**
     * Produces function task that takes two non-lambda arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param in1   provides first value for fn
     * @param in2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> base type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> SupplierTask<R> fnTask(CompletableFuture<IN1> in1, CompletableFuture<IN2> in2, BiFunction<IN1, IN2, R> fn) {
        return task(new SupplierTask.SupplierTask2<>(in1, in2, fn));
    }

    /**
     * Produces function task value that takes two non-lambda arguments and produces one result.
     * Method invocation is light by default.
     *
     * @param in1   provides first value for fn
     * @param in2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> base type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> CompletableFuture<R> fn(CompletableFuture<IN1> in1, CompletableFuture<IN2> in2, BiFunction<IN1, IN2, R> fn) {
        return fnTask(in1, in2, fn).apply();
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

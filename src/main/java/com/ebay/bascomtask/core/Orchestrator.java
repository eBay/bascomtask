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
import java.util.concurrent.CompletionStage;
import java.util.function.*;

/**
 * Manages execution flow among one or more tasks.
 *
 * @author Brendan McCarthy
 */
public interface Orchestrator extends CommonConfig, SpawnMode.SpawnModable {

    /**
     * Creates an Orchestrator.
     *
     * @return new Orchestrator
     */
    static Orchestrator create() {
        return new Engine();
    }

    /**
     * Returns details for any future previously registered with {@link #execute(CompletionStage[])} (directly or
     * through an operation on a CompletableFuture return value).
     *
     * @param cf to map
     * @return unique TaskMeta for supplied argument or null if no match
     */
    TaskMeta getTaskMeta(CompletableFuture<?> cf);

    /**
     * Returns {@link #getSpawnMode()} or, if null, the globally-configured spawn mode.
     *
     * @return non-null spawnMode
     */
    SpawnMode getEffectiveSpawnMode();

    /**
     * Initiates execution of the task methods behind each supplied CompletableFuture if they are not already started,
     * as well as the task methods needed to supply its inputs, recursively. The dependency ordering is automatically
     * determined and strictly maintained such that ech task method is only executed when all its arguments have
     * already completed and their CompletableFutures have completed, so a call to get their values will not block.
     *
     * <p>Each task method will only be executed once even it is passed multiple times to this method. As this method
     * is called implicitly for any operation on a single future returned from a task, this method call is only needed
     * to start multiple futures at once or for other situations where a singular waiting read access is not desired.
     *
     * <p>This call only starts but does not necessarily wait for any of the started tasks to complete. More specifically,
     * it does not wait on any threads that are spawned to complete. Access a value on any BT-managed CompletableFuture
     * or call {@link #executeAndWait(CompletableFuture[])} to force waiting.
     *
     * @param futures to execute
     */
    void execute(CompletionStage<?>... futures);

    /**
     * Variant of {@link #execute(CompletionStage[])} that waits for all of its arguments to finish and
     * complete (meaning any call to get their value will not block).
     *
     * @param futures to execute and then wait on
     */
    void executeAndWait(CompletableFuture<?>... futures);

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
     * arguments indicating whether to proactively start executing the thenFuture and/or the elseFuture when
     * condition is activated. That would be before the result of condition is known, so proactively starting
     * either future may be wasteful but it will also be faster.
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
     * Creates a task wrapper on a user pojo that implements an interface X that in turn implements TaskInterface&lt;X&gt;.
     * The result is a wrapper object that has the same signature as its pojo argument, such that any
     * CompletableFuture-returning task methods invoked on this wrapper are not executed right away -- they will
     * be executed by if/when any read operation on the returned CompletableFuture or by passing that CompletableFuture
     * to {@link #execute(CompletionStage[])}. Because of that lazy evaluation, tasks can be added with little
     * performance penalty while only later choosing which ones are actually needed. The userTask argument can be
     * freely wrapped any number of times by calling this method (as well as similar methods).
     *
     * <p>A 'read operation' in this context refers to standard CompletableFuture access operations that initiate execution.
     * This includes simple operations such as {@link CompletableFuture#get()} as well as any composition operations
     * such as {@link CompletableFuture#thenApply(Function)} with the exception of the 'compose' variations such as
     * {@link CompletableFuture#thenCompose(Function)} whose _fn_ argument can only be started with
     * {@link #execute(CompletionStage[])}.
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
        return fn(() -> userTask, fn).apply();
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
        return vfn(() -> userTask, fn).apply();
    }


    //////////////////////////////////////////////
    // Function tasks turn lambdas into tasks. ///
    //////////////////////////////////////////////

    /**
     * Produces function task that takes no arguments and produces one result.
     *
     * @param fn  function to apply to that pojo if/when task is activated
     * @param <R> type of return result
     * @return Function task
     */
    default <R> SupplierTask<R> fn(Supplier<R> fn) {
        return task(new SupplierTask.SupplierTask0<>(fn));
    }

    /**
     * Produces function task that takes one argument and produces one result.
     *
     * @param s1   Supplier function (returns a value)
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> type of input
     * @param <R>  type of return result
     * @return Function task
     */
    default <IN, R> SupplierTask<R> fn(Supplier<IN> s1, Function<IN, R> fn) {
        CompletableFuture<IN> in1 = fn(s1).apply();
        return task(new SupplierTask.SupplierTask1<>(in1, fn));
    }

    /**
     * Produces function task that takes one non-lambda argument and produces one result.
     *
     * @param in   provides result to be applied to function
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> base type of input
     * @param <R>  type of return result
     * @return Function task
     */
    default <IN, R> SupplierTask<R> fn(CompletableFuture<IN> in, Function<IN, R> fn) {
        return task(new SupplierTask.SupplierTask1<>(in, fn));
    }

    /**
     * Produces function task that takes two non-lambda arguments and produces one result.
     *
     * @param in1   provides first value for fn
     * @param in2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> base type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> SupplierTask<R> fn(CompletableFuture<IN1> in1, CompletableFuture<IN2> in2, BiFunction<IN1, IN2, R> fn) {
        return task(new SupplierTask.SupplierTask2<>(in1, in2, fn));
    }

    /**
     * Produces function task that takes mixed arguments and produces one result.
     *
     * @param s1    provides first value for fn
     * @param in2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> base type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> SupplierTask<R> fn(Supplier<IN1> s1, CompletableFuture<IN2> in2, BiFunction<IN1, IN2, R> fn) {
        CompletableFuture<IN1> in1 = fn(s1).apply();
        return fn(in1, in2, fn);
    }

    /**
     * Produces function task that takes mixed arguments and produces one result.
     *
     * @param in1   provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> SupplierTask<R> fn(CompletableFuture<IN1> in1, Supplier<IN2> s2, BiFunction<IN1, IN2, R> fn) {
        CompletableFuture<IN2> in2 = fn(s2).apply();
        return fn(in1, in2, fn);
    }

    /**
     * Produces function task that takes two lambda arguments and produces one result.
     *
     * @param s1    provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> type of input2
     * @param <R>   type of return result
     * @return Function task
     */
    default <IN1, IN2, R> SupplierTask<R> fn(Supplier<IN1> s1, Supplier<IN2> s2, BiFunction<IN1, IN2, R> fn) {
        CompletableFuture<IN1> in1 = fn(s1).apply();
        CompletableFuture<IN2> in2 = fn(s2).apply();
        return fn(in1, in2, fn);
    }

    /**
     * Produces function task that takes one lambda argument and produces no result.
     *
     * @param s1   provides value for fn
     * @param fn   function to apply to that pojo if/when task is activated
     * @param <IN> type of input1
     * @return Function task
     */
    default <IN> ConsumerTask vfn(Supplier<IN> s1, Consumer<IN> fn) {
        CompletableFuture<IN> in1 = fn(s1).apply();
        return task(new ConsumerTask.ConsumerTask1<>(in1, fn));
    }

    /**
     * Produces function task that takes mixed arguments and produces no result.
     *
     * @param cf1   provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> type of input2
     * @return Function task
     */
    default <IN1, IN2> ConsumerTask vfn(CompletableFuture<IN1> cf1, Supplier<IN2> s2, BiConsumer<IN1, IN2> fn) {
        CompletableFuture<IN2> in2 = fn(s2).apply();
        return task(new ConsumerTask.ConsumerTask2<>(cf1, in2, fn));
    }

    /**
     * Produces function task that takes non-lambda arguments and produces no result.
     *
     * @param cf1   provides first value for fn
     * @param cf2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> base type of input1
     * @param <IN2> base type of input2
     * @return Function task
     */
    default <IN1, IN2> ConsumerTask vfn(CompletableFuture<IN1> cf1, CompletableFuture<IN2> cf2, BiConsumer<IN1, IN2> fn) {
        return task(new ConsumerTask.ConsumerTask2<>(cf1, cf2, fn));
    }

    /**
     * Produces function task that takes mixed arguments and produces no result.
     *
     * @param s1    provides first value for fn
     * @param cf2   provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> base type of input2
     * @return Function task
     */
    default <IN1, IN2> ConsumerTask vfn(Supplier<IN1> s1, CompletableFuture<IN2> cf2, BiConsumer<IN1, IN2> fn) {
        CompletableFuture<IN1> in1 = fn(s1).apply();
        return task(new ConsumerTask.ConsumerTask2<>(in1, cf2, fn));
    }

    /**
     * Produces function task that takes two lambda arguments and produces no result.
     *
     * @param s1    provides first value for fn
     * @param s2    provides second value for fn
     * @param fn    function to apply to that pojo if/when task is activated
     * @param <IN1> type of input1
     * @param <IN2> type of input2
     * @return Function task
     */
    default <IN1, IN2> ConsumerTask vfn(Supplier<IN1> s1, Supplier<IN2> s2, BiConsumer<IN1, IN2> fn) {
        CompletableFuture<IN1> in1 = fn(s1).apply();
        CompletableFuture<IN2> in2 = fn(s2).apply();
        return task(new ConsumerTask.ConsumerTask2<>(in1, in2, fn));
    }
}

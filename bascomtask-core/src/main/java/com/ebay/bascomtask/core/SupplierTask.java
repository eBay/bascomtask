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

import com.ebay.bascomtask.annotations.Light;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Function tasks added through 'fn()' methods on {@link Orchestrator} allow CompletableFuture tasks to
 * be created from lambda expressions that supply a return value. The inputs are bundled into the task,
 * and the result is obtained by invoking {@link #apply()} on this task.
 *
 * @author Brendan McCarthy
 */
public interface SupplierTask<R> extends TaskInterface<SupplierTask<R>> {
    /**
     * Creates a CompletableFuture around the lambda expression.
     *
     * @return evaluated CompletableFuture
     */
    CompletableFuture<R> apply();

    abstract class BaseSupplierTask<R> extends Binding<R> implements SupplierTask<R> {
        private String name = null;

        public BaseSupplierTask(Engine engine) {
            super(engine);
        }

        @Override
        public BaseSupplierTask<R> name(String name) {
            this.name = name;
            return this;
        }

        @Override
        String doGetExecutionName() {
            if (name==null) {
                return "FunctionTask";
            } else {
                return name;
            }
        }

        @Override
        public TaskInterface<?> getTask() {
            return null;
        }

        @Override
        public void formatActualSignature(StringBuilder sb) {

        }

        @Override
        protected Object invokeTaskMethod() {
            throw new RuntimeException("Invalid internal state for function task " + this);
        }
    }

    /**
     * An SupplierTask that takes no arguments.
     *
     * @param <R> type of return result
     */
    class SupplierTask0<R> extends BaseSupplierTask<R> {
        private final Supplier<R> fn;

        public SupplierTask0(Engine engine, Supplier<R> fn) {
            super(engine);
            this.fn = fn;
        }

        @Override
        @Light
        public CompletableFuture<R> apply() {
            return complete(fn.get());
        }
    }

    /**
     * An SupplierTask that takes 1 argument.
     *
     * @param <T> type of input
     * @param <R>  type of return result
     */
    class SupplierTask1<T, R> extends BaseSupplierTask<R> {
        private final Function<T, R> fn;
        final BascomTaskFuture<T> input;

        public SupplierTask1(Engine engine, CompletableFuture<T> input, Function<T, R> fn) {
            super(engine);
            this.fn = fn;
            this.input = ensureWrapped(input,true);
        }

        @Override
        Binding<?> doActivate(Binding<?> pending, TimeBox timeBox) {
            return input.activate(this, pending, timeBox);
        }

        @Override
        @Light
        public CompletableFuture<R> apply() {
            T value = get(input);
            return complete(fn.apply(value));
        }
    }

    /**
     * An SupplierTask that takes 2 arguments.
     *
     * @param <T> type of input
     * @param <R>  type of return result
     */
    class SupplierTask2<T,U,R> extends BaseSupplierTask<R> {
        private final BiFunction<T,U,R> fn;
        final BascomTaskFuture<T> firstInput;
        final BascomTaskFuture<U> secondInput;

        public SupplierTask2(Engine engine, CompletableFuture<T> firstInput, CompletableFuture<U> secondInput, BiFunction<T,U,R> fn) {
            super(engine);
            this.fn = fn;
            this.firstInput = ensureWrapped(firstInput,true);
            this.secondInput = ensureWrapped(secondInput,true);
        }

        @Override
        Binding<?> doActivate(Binding<?> pending, TimeBox timeBox) {
            pending = firstInput.activate(this, pending, timeBox);
            return secondInput.activate(this,pending,timeBox);
        }

        @Override
        @Light
        public CompletableFuture<R> apply() {
            T firstValue = get(firstInput);
            U secondValue = get(secondInput);
            return complete(fn.apply(firstValue,secondValue));
        }
    }
}

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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Function tasks added through 'fn()' methods on {@link Orchestrator} allow CompletableFuture tasks to
 * be created from lambda expressions that supply a return value.
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

    /**
     * Convenience method that evaluates lambda expression right away.
     *
     * @return evaluated value
     */
    default R get() {
        return get(apply());
    }

    /**
     * An SupplierTask that takes no arguments.
     *
     * @param <R> type of return result
     */
    class SupplierTask0<R> implements SupplierTask<R> {
        private final Supplier<R> fn;

        public SupplierTask0(Supplier<R> fn) {
            this.fn = fn;
        }

        @Override
        public CompletableFuture<R> apply() {
            return complete(fn.get());
        }
    }

    /**
     * An SupplierTask that takes 1 argument.
     *
     * @param <IN> type of input
     * @param <R>  type of return result
     */
    class SupplierTask1<IN, R> implements SupplierTask<R> {
        private final Function<IN, R> fn;
        private final CompletableFuture<IN> cf;

        public SupplierTask1(CompletableFuture<IN> cf, Function<IN, R> fn) {
            this.cf = cf;
            this.fn = fn;
        }

        @Override
        public CompletableFuture<R> apply() {
            IN v = get(cf);
            return complete(fn.apply(v));
        }
    }

    /**
     * An SupplierTask that takes 2 arguments.
     *
     * @param <IN1> type of first input
     * @param <IN2> type of second input
     * @param <R>   type of return result
     */
    class SupplierTask2<IN1, IN2, R> implements SupplierTask<R> {
        private final BiFunction<IN1, IN2, R> fn;
        private final CompletableFuture<IN1> cf1;
        private final CompletableFuture<IN2> cf2;

        public SupplierTask2(CompletableFuture<IN1> cf1, CompletableFuture<IN2> cf2, BiFunction<IN1, IN2, R> fn) {
            this.cf1 = cf1;
            this.cf2 = cf2;
            this.fn = fn;
        }

        @Override
        public CompletableFuture<R> apply() {
            IN1 v1 = get(cf1);
            IN2 v2 = get(cf2);
            return complete(fn.apply(v1, v2));
        }
    }
}

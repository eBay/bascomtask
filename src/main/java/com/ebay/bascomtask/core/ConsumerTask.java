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
import java.util.function.*;

/**
 * Function tasks added through 'fn()' methods on {@link Orchestrator} allow CompletableFuture tasks to
 * be created from lambda expressions that do not return a value.
 *
 * @author Brendan McCarthy
 */
public interface ConsumerTask extends TaskInterface<ConsumerTask> {
    /**
     * Creates a CompletableFuture around the lambda expression.
     * @return evaluated CompletableFuture
     */
    CompletableFuture<Void> apply();

    /**
     * An ConsumerTask that takes 1 argument.
     * @param <IN> type of input
     */
    class ConsumerTask1<IN> implements ConsumerTask {
        private final Consumer<IN> fn;
        private final CompletableFuture<IN> cf;

        public ConsumerTask1(CompletableFuture<IN> cf, Consumer<IN> fn) {
            this.cf = cf;
            this.fn = fn;
        }

        @Override
        public CompletableFuture<Void> apply() {
            IN v = get(cf);
            fn.accept(v);
            return complete();
        }
    }

    /**
     * An ConsumerTask that takes 2 arguments.
     * @param <IN1> type of first input
     * @param <IN2> type of second input
     */
    class ConsumerTask2<IN1,IN2> implements ConsumerTask {
        private final BiConsumer<IN1,IN2> fn;
        private final CompletableFuture<IN1> cf1;
        private final CompletableFuture<IN2> cf2;

        public ConsumerTask2(CompletableFuture<IN1> cf1, CompletableFuture<IN2> cf2, BiConsumer<IN1,IN2> fn) {
            this.cf1 = cf1;
            this.cf2 = cf2;
            this.fn = fn;
        }

        @Override
        public CompletableFuture<Void> apply() {
            IN1 v1 = get(cf1);
            IN2 v2 = get(cf2);
            fn.accept(v1,v2);
            return complete();
        }
    }
}

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

/**
 * Task that always throws exceptions.
 *
 * @author Brendan Mccarthy
 */
public interface ExceptionTask<RET> extends TaskInterface<ExceptionTask<RET>> {

    class FaultHappened extends RuntimeException {
        public FaultHappened(String msg) {
            super(msg);
        }
    }

    CompletableFuture<RET> faultImmediate(String msg);

    CompletableFuture<RET> faultAfter(int ms, String msg);

    CompletableFuture<RET> faultAfter(CompletableFuture<RET> x, int ms, String msg);

    CompletableFuture<RET> faultImmediateCompletion(int ms, String msg);

    /**
     * Throws Exception after interval in external (non-BT) CompletableFuture-managed thread.
     *
     * @param ms  to wait before throwing
     * @param msg to include in exceptino
     * @return !isDone() CompletableFuture
     */
    CompletableFuture<RET> faultWithCompletionAfter(int ms, String msg);

    static <T> Faulty<T> faulty() {
        return new Faulty<>();
    }

    class Faulty<RET> implements ExceptionTask<RET> {

        private CompletableFuture<RET> fault(int ms, String msg) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                throw new RuntimeException("Bad interrupt", e);
            }
            throw new FaultHappened(msg);
        }

        private RET delayFault(int ms, String msg) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                throw new RuntimeException("Bad interrupt", e);
            }
            throw new FaultHappened(msg);
        }

        private CompletableFuture<RET> faultWithCompletion(int ms, String msg) {
            return CompletableFuture.supplyAsync(() -> delayFault(ms, msg));
        }

        @Override
        public CompletableFuture<RET> faultImmediate(String msg) {
            throw new FaultHappened(msg);
        }

        @Override
        public CompletableFuture<RET> faultAfter(int ms, String msg) {
            return fault(ms, msg);
        }

        @Override
        public CompletableFuture<RET> faultAfter(CompletableFuture<RET> x, int ms, String msg) {
            return fault(ms, msg);
        }

        @Override
        public CompletableFuture<RET> faultImmediateCompletion(int ms, String msg) {
            return CompletableFuture.supplyAsync(() -> {
                throw new FaultHappened(msg);
            });
        }

        @Override
        public CompletableFuture<RET> faultWithCompletionAfter(int ms, String msg) {
            return faultWithCompletion(ms, msg);
        }
    }

}

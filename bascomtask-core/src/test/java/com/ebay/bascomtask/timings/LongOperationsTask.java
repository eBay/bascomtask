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
package com.ebay.bascomtask.timings;

import com.ebay.bascomtask.core.TaskInterface;

import java.util.concurrent.CompletableFuture;

/**
 * Task with simple arithmetic operations.
 *
 * @author Brendan McCarthy
 */
public interface LongOperationsTask extends TaskInterface<LongOperationsTask> {
    CompletableFuture<Long> ret(long v);

    CompletableFuture<Long> inc(CompletableFuture<Long> cf);

    CompletableFuture<Long> add(CompletableFuture<Long> cf1, CompletableFuture<Long> cf2);

    CompletableFuture<Long> add(CompletableFuture<Long> cf1, CompletableFuture<Long> cf2, CompletableFuture<Long> cf3);

    class LongOperationsTaskImpl implements LongOperationsTask {

        @Override
        public CompletableFuture<Long> ret(long v) {
            return complete(v);
        }

        @Override
        public CompletableFuture<Long> inc(CompletableFuture<Long> cf) {
            long v = get(cf);
            return complete(v + 1);
        }

        @Override
        public CompletableFuture<Long> add(CompletableFuture<Long> cf1, CompletableFuture<Long> cf2) {
            long x = get(cf1);
            long y = get(cf2);
            return complete(x + y);

        }

        @Override
        public CompletableFuture<Long> add(CompletableFuture<Long> cf1, CompletableFuture<Long> cf2, CompletableFuture<Long> cf3) {
            long x = get(cf1);
            long y = get(cf2);
            long z = get(cf3);
            return complete(x + y + z);
        }
    }
}

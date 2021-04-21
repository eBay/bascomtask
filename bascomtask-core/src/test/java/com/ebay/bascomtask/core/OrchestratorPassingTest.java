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

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;

/**
 * Passing Orchestrators to nested tasks.
 *
 * @author Brendan McCarthy
 */
public class OrchestratorPassingTest extends BaseOrchestratorTest {

    private static int FIXED_VALUE = 5;

    interface IFooTask extends TaskInterface<IFooTask> {
        CompletableFuture<Integer> simple();
        CompletableFuture<Integer> external();
        CompletableFuture<CompletableFuture<Integer>> nestSimple();
        CompletableFuture<CompletableFuture<Integer>> nestExternal();
    }

    static class FooTask implements IFooTask {

        @Override
        public CompletableFuture<Integer> simple() {
            return complete(FIXED_VALUE);
        }
        @Override
        public CompletableFuture<CompletableFuture<Integer>> nestSimple() {
            Orchestrator nested = Orchestrator.current();
            CompletableFuture<Integer> sv = nested.task(new FooTask()).simple();
            return complete(sv);
        }
        @Override
        public CompletableFuture<Integer> external() {
            return CompletableFuture.supplyAsync(()->{
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return FIXED_VALUE;
            });
        }
        @Override
        public CompletableFuture<CompletableFuture<Integer>> nestExternal() {
            Orchestrator nested = Orchestrator.current();
            CompletableFuture<Integer> sv = nested.task(new FooTask()).simple();
            return complete(sv);
        }
    }

    @Test
    public void nested() throws Exception {
        Orchestrator $ = Orchestrator.create();
        CompletableFuture<CompletableFuture<Integer>> got = $.task(new FooTask()).nestSimple();
        CompletableFuture<Integer> c1 = got.get();
        Integer v = c1.get();
        assertEquals(FIXED_VALUE,(int)v);
    }

    @Test
    public void external() throws Exception {
        Orchestrator $ = Orchestrator.create();
        CompletableFuture<CompletableFuture<Integer>> got = $.task(new FooTask()).nestExternal();
        CompletableFuture<Integer> c1 = got.get();
        Integer v = c1.get();
        assertEquals(FIXED_VALUE,(int)v);
    }
}

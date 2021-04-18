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

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Test CompletableFutures activation.
 *
 * @author Brendan McCarthy
 */
public class BascomTaskFutureTest extends BaseOrchestratorTest {

    private static final Executor executor = Executors.newFixedThreadPool(5);
    private CountingTask.Impl currentTask;

    private interface CountingTask extends TaskInterface<CountingTask> {
        CompletableFuture<String> rets(String s);

        CompletableFuture<Integer> ret(int v);

        CompletableFuture<Integer> retHit(int v);

        CompletableFuture<Integer> fault(CompletableFuture<Integer> cf);

        CompletableFuture<String> faults(CompletableFuture<String> cf);

        class Impl implements CountingTask {
            final int exp;
            final AtomicInteger count = new AtomicInteger(0);
            final AtomicInteger holder = new AtomicInteger(0);

            Impl(int exp) {
                this.exp = exp;
            }

            public int hit() {
                return count.incrementAndGet();
            }

            @Override
            public CompletableFuture<String> rets(String s) {
                return complete(s);
            }

            @Override
            public CompletableFuture<Integer> ret(int v) {
                return complete(v);
            }

            @Override
            public CompletableFuture<Integer> retHit(int v) {
                hit();
                return ret(v);
            }

            @Override
            public CompletableFuture<Integer> fault(CompletableFuture<Integer> cf) {
                throw new RuntimeException("Fault!!!");
            }

            @Override
            public CompletableFuture<String> faults(CompletableFuture<String> cf) {
                throw new RuntimeException("Fault!!!");
            }
        }
    }

    private CountingTask task(int exp) {
        currentTask = new CountingTask.Impl(exp);
        return currentTask;
    }

    private int hit() {
        return currentTask.hit();
    }

    private void hit(int exp, int got) {
        assertEquals(exp, got);
        hit();
    }

    private void verify(int exp, int got, Throwable ex) {
        assertEquals(exp, got);
        assertNull(ex);
    }

    @After
    public void after() {
        super.after();
        sleep(10); // Give time for async routines to execute
        if (currentTask != null) { // This being called even before tests for some reason
            assertEquals(currentTask.exp, currentTask.count.get());
        }
    }

    @Test
    public void toStringFormat() {
        final String NAME = "foo-bar-baz";
        CompletableFuture<Integer> cf = $.task(task(0)).name(NAME).ret(0);
        assertTrue(cf.toString().contains(NAME));
    }

    @Test
    public void get() throws Exception {
        CompletableFuture<Integer> cf = $.task(task(1)).retHit(8);
        assertEquals(8, (int) cf.get());
    }

    @Test
    public void join() {
        CompletableFuture<Integer> cf = $.task(task(1)).retHit(8);
        assertEquals(8, (int) cf.join());
    }

    @Test
    public void getNow() {
        CompletableFuture<Integer> cf = $.task(task(1)).retHit(8);
        assertEquals(8, (int) cf.getNow(99));
    }

    @Test
    public void thenAccept() {
        CompletableFuture<Integer> cf = $.task(task(0)).ret(1);
        assertNotNull(cf.thenAccept(v -> hit()));
    }

    @Test
    public void thenAcceptActivate() {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        $.activate(cf);
        assertNotNull(cf.thenAccept(v -> hit()));
    }

    @Test
    public void thenApplyThenApply() throws Exception {
        CompletableFuture<Integer> cf = $.task(task(2)).ret(1);

        CompletableFuture<Integer> c2 = cf.thenApply(v -> hit() + v + 10).thenApply(v -> hit() + v + 20);
        $.activate(cf);
        assertEquals(34, (int) c2.get());
    }

    @Test
    public void thenApplyThenAccept() throws Exception {
        CompletableFuture<Integer> cf = $.task(task(2)).ret(1);
        CompletableFuture<Void> c2 = cf.thenApply(v -> hit() + v + 10).thenAccept(v -> hit());
        $.activate(cf);
        c2.get();
    }

    @Test
    public void thenCombineAsyncExecutor() throws Exception {
        CountingTask task = task(0);
        CompletableFuture<Integer> cf1 = $.task(task).ret(1);
        CompletableFuture<Integer> cf2 = $.task(task).ret(1);
        CompletableFuture<Integer> combine = cf1.thenCombineAsync(cf2, Integer::sum, executor);
        $.activate(cf1,cf2);
        int got = combine.get();
        assertEquals(2, got);
    }

    @Test
    public void thenCompose() throws Exception {
        final int RV = 9;
        CountingTask task = task(0);
        CompletableFuture<Integer> cf1 = $.task(task).ret(1);
        CompletableFuture<Integer> cf2 = $.task(task).ret(RV);
        CompletableFuture<Integer> compose = cf1.thenCompose(v -> cf2);
        $.activate(cf1,cf2);
        int got = compose.get();
        assertEquals(RV, got);
    }

    @Test
    public void applyToEither() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Integer> combine = cf1.applyToEither(cf2, x -> x * 5);
        $.activate(cf1,cf2);
        int got = combine.get();
        assertEquals(5, got);
    }

    @Test
    public void applyToEitherAsync() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Integer> combine = cf1.applyToEitherAsync(cf2, x -> x * 5);
        $.activate(cf1,cf2);
        int got = combine.get();
        assertEquals(5, got);
    }

    @Test
    public void applyToEitherAsyncExecutor() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Integer> combine = cf1.applyToEitherAsync(cf2, x -> x * 5, executor);
        $.activate(cf1,cf2);
        int got = combine.get();
        assertEquals(5, got);
    }

    @Test
    public void acceptEitherAsync() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> either = cf1.acceptEitherAsync(cf2, x -> currentTask.holder.set(x));
        $.activateAndWait(cf1,cf2);
        sleep(20);
        assertEquals(1, currentTask.holder.get());
    }

    @Test
    public void whenComplete() throws Exception {
        int expectedValue = 567;
        CompletableFuture<Integer> cf1 = $.task(task(1)).retHit(expectedValue);
        cf1.join();
        int got = cf1.whenComplete((v, ex) -> verify(expectedValue, v, ex)).get();
        assertEquals(expectedValue,got);
    }

    @Test
    public void whenCompleteAsync() throws Exception {
        int expectedValue = 567;
        CompletableFuture<Integer> cf1 = $.task(task(1)).retHit(expectedValue);
        cf1.join();
        int got = cf1.whenCompleteAsync((v, ex) -> verify(expectedValue, v, ex)).get();
        assertEquals(expectedValue,got);
    }
}

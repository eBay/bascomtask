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
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenAccept(v -> hit()));
    }

    @Test
    public void thenAcceptAsync() {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenAcceptAsync(v -> hit()));
    }

    @Test
    public void thenAcceptAsyncExecutor() {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenAcceptAsync(v -> hit(), executor));
    }


    @Test
    public void thenAcceptBoth() {
        CompletableFuture<Integer> other = $.task(task(1)).ret(5);
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenAcceptBoth(other, (x, y) -> hit(6, x + y)));
    }

    @Test
    public void thenAcceptBothAsync() {
        CompletableFuture<Integer> other = $.task(task(1)).ret(5);
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenAcceptBothAsync(other, (x, y) -> hit(6, x + y)));
    }

    @Test
    public void thenAcceptBothAsyncExecutor() {
        CompletableFuture<Integer> other = $.task(task(1)).ret(3);
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenAcceptBothAsync(other, (x, y) -> hit(4, x + y), executor));
    }


    @Test
    public void thenRun() {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenRun(this::hit));
    }

    @Test
    public void thenRunAsync() {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenRunAsync(this::hit));
    }

    @Test
    public void thenRunAsyncExecutor() {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenRunAsync(this::hit, executor));
    }


    @Test
    public void thenApply() {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        assertNotNull(cf.thenApply(v -> hit()));
    }

    @Test
    public void thenApplyThenApply() throws Exception {
        CompletableFuture<Integer> cf = $.task(task(2)).ret(1);

        CompletableFuture<Integer> c2 = cf.thenApply(v -> hit() + v + 10).thenApply(v -> hit() + v + 20);
        assertEquals(34, (int) c2.get());
    }

    @Test
    public void thenApplyThenAccept() throws Exception {
        CompletableFuture<Integer> cf = $.task(task(2)).ret(1);
        CompletableFuture<Void> c2 = cf.thenApply(v -> hit() + v + 10).thenAccept(v -> hit());
        c2.get();
    }

    @Test
    public void thenApplyAsync() throws Exception {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        int got = cf.thenApplyAsync(v -> hit()).get();
        assertEquals(1, got);
    }

    @Test
    public void thenApplyAsyncExecutor() throws Exception {
        CompletableFuture<Integer> cf = $.task(task(1)).ret(1);
        int got = cf.thenApplyAsync(v -> hit(), executor).get();
        assertEquals(1, got);
    }

    @Test
    public void thenCombine() throws Exception {
        CountingTask task = task(0);
        CompletableFuture<Integer> cf1 = $.task(task).ret(1);
        CompletableFuture<Integer> cf2 = $.task(task).ret(1);
        CompletableFuture<Integer> combine = cf1.thenCombine(cf2, Integer::sum);
        int got = combine.get();
        assertEquals(2, got);
    }

    @Test
    public void thenCombineAsync() throws Exception {
        CountingTask task = task(0);
        CompletableFuture<Integer> cf1 = $.task(task).ret(1);
        CompletableFuture<Integer> cf2 = $.task(task).ret(1);
        CompletableFuture<Integer> combine = cf1.thenCombineAsync(cf2, Integer::sum);
        int got = combine.get();
        assertEquals(2, got);
    }

    @Test
    public void thenCombineAsyncExecutor() throws Exception {
        CountingTask task = task(0);
        CompletableFuture<Integer> cf1 = $.task(task).ret(1);
        CompletableFuture<Integer> cf2 = $.task(task).ret(1);
        CompletableFuture<Integer> combine = cf1.thenCombineAsync(cf2, Integer::sum, executor);
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
        $.execute(cf2);  // Required because cf2 is otherwise invisible
        int got = compose.get();
        assertEquals(RV, got);
    }

    @Test
    public void thenComposeAsync() throws Exception {
        final int RV = 9;
        CountingTask task = task(0);
        CompletableFuture<Integer> cf1 = $.task(task).ret(1);
        CompletableFuture<Integer> cf2 = $.task(task).ret(RV);
        CompletableFuture<Integer> compose = cf1.thenComposeAsync(v -> cf2);
        $.execute(cf2);  // Required because cf2 is otherwise invisible
        int got = compose.get();
        assertEquals(RV, got);
    }

    @Test
    public void thenComposeAsyncExecutor() throws Exception {
        final int RV = 9;
        CountingTask task = task(0);
        CompletableFuture<Integer> cf1 = $.task(task).ret(1);
        CompletableFuture<Integer> cf2 = $.task(task).ret(RV);
        CompletableFuture<Integer> compose = cf1.thenComposeAsync(v -> cf2, executor);
        $.execute(cf2);  // Required because cf2 is otherwise invisible
        int got = compose.get();
        assertEquals(RV, got);
    }

    @Test
    public void thenComposeCf() throws Exception {
        CountingTask task = task(0);
        CompletableFuture<Integer> cf1 = $.task(task).ret(2);
        CompletableFuture<Integer> compose = cf1.thenCompose(x -> CompletableFuture.supplyAsync(() -> x * 4));
        int got = compose.get();
        assertEquals(8, got);
    }

    @Test
    public void applyToEither() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Integer> combine = cf1.applyToEither(cf2, x -> x * 5);
        int got = combine.get();
        assertEquals(5, got);
    }

    @Test
    public void applyToEitherAsync() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Integer> combine = cf1.applyToEitherAsync(cf2, x -> x * 5);
        int got = combine.get();
        assertEquals(5, got);
    }

    @Test
    public void applyToEitherAsyncExecutor() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Integer> combine = cf1.applyToEitherAsync(cf2, x -> x * 5, executor);
        int got = combine.get();
        assertEquals(5, got);
    }

    @Test
    public void runAfterEither() throws Exception {
        final int RV = 9;
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> combine = cf1.runAfterEither(cf2, () -> currentTask.holder.set(RV));
        combine.get();
        assertEquals(RV, currentTask.holder.get());
    }

    @Test
    public void runAfterEitherAsync() throws Exception {
        final int RV = 9;
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> combine = cf1.runAfterEitherAsync(cf2, () -> currentTask.holder.set(RV));
        combine.get();
        assertEquals(RV, currentTask.holder.get());
    }

    @Test
    public void runAfterEitherAsyncExecutor() throws Exception {
        final int RV = 9;
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> combine = cf1.runAfterEitherAsync(cf2, () -> currentTask.holder.set(RV), executor);
        combine.get();
        assertEquals(RV, currentTask.holder.get());
    }

    @Test
    public void runAfterBoth() throws Exception {
        final int RV = 9;
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> combine = cf1.runAfterBoth(cf2, () -> currentTask.holder.set(RV));
        combine.get();
        assertEquals(RV, currentTask.holder.get());
    }

    @Test
    public void runAfterBothAsync() throws Exception {
        final int RV = 9;
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> combine = cf1.runAfterBothAsync(cf2, () -> currentTask.holder.set(RV));
        combine.get();
        assertEquals(RV, currentTask.holder.get());
    }

    @Test
    public void runAfterBothAsyncExecutor() throws Exception {
        final int RV = 9;
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> combine = cf1.runAfterBothAsync(cf2, () -> currentTask.holder.set(RV), executor);
        combine.get();
        assertEquals(RV, currentTask.holder.get());
    }

    @Test
    public void acceptEither() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> either = cf1.acceptEither(cf2, x -> currentTask.holder.set(x));
        either.get();
        assertEquals(1, currentTask.holder.get());
    }

    @Test
    public void acceptEitherAsync() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> either = cf1.acceptEitherAsync(cf2, x -> currentTask.holder.set(x));
        either.get();
        assertEquals(1, currentTask.holder.get());
    }

    @Test
    public void acceptEitherAsyncExecutor() throws Exception {
        CountingTask task = task(2);
        CompletableFuture<Integer> cf1 = $.task(task).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task).retHit(1);
        CompletableFuture<Void> either = cf1.acceptEitherAsync(cf2, x -> currentTask.holder.set(x), executor);
        either.get();
        assertEquals(1, currentTask.holder.get());
    }

    @Test
    public void whenComplete() throws Exception {
        int expectedValue = 567;
        CompletableFuture<Integer> cf1 = $.task(task(1)).retHit(expectedValue);
        int got = cf1.whenComplete((v, ex) -> verify(expectedValue, v, ex)).get();
        assertEquals(expectedValue,got);
    }

    @Test
    public void whenCompleteAsync() throws Exception {
        int expectedValue = 567;
        CompletableFuture<Integer> cf1 = $.task(task(1)).retHit(expectedValue);
        int got = cf1.whenCompleteAsync((v, ex) -> verify(expectedValue, v, ex)).get();
        assertEquals(expectedValue,got);
    }

    @Test
    public void whenCompleteAsyncExecutor() throws Exception {
        int expectedValue = 567;
        CompletableFuture<Integer> cf1 = $.task(task(1)).retHit(expectedValue);
        int got = cf1.whenCompleteAsync((v, ex) -> verify(expectedValue, v, ex),executor).get();
        assertEquals(expectedValue,got);
    }

    @Test
    public void handle() throws Exception {
        final int HV = 11;
        CompletableFuture<String> cf1 = $.task(task(0)).rets("no_matter");
        CompletableFuture<Integer> cf2 = $.task(task(0)).faults(cf1).handle((x, y) -> HV);
        int got = cf2.get();
        assertEquals(HV, got);
    }

    @Test
    public void handleAsync() throws Exception {
        final int HV = 11;
        CompletableFuture<String> cf1 = $.task(task(0)).rets("no_matter");
        CompletableFuture<Integer> cf2 = $.task(task(0)).faults(cf1).handleAsync((x, y) -> HV);
        int got = cf2.get();
        assertEquals(HV, got);
    }

    @Test
    public void handleAsyncExecutor() throws Exception {
        final int HV = 11;
        CompletableFuture<String> cf1 = $.task(task(0)).rets("no_matter");
        CompletableFuture<Integer> cf2 = $.task(task(0)).faults(cf1).handleAsync((x, y) -> HV, executor);
        int got = cf2.get();
        assertEquals(HV, got);
    }


    @Test
    public void exceptionally() throws Exception {
        final int HV = 999;
        CompletableFuture<Integer> cf1 = $.task(task(1)).retHit(1);
        CompletableFuture<Integer> cf2 = $.task(task(0)).fault(cf1).exceptionally(e -> HV);
        int got = cf2.get();
        assertEquals(HV, got);
    }


}

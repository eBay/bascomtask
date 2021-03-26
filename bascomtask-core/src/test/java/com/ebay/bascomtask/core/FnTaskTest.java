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

import static com.ebay.bascomtask.core.UberTask.task;
import static org.junit.Assert.*;

/**
 * Function task tests
 *
 * @author Brendan McCarthy
 */
public class FnTaskTest extends BaseOrchestratorTest {

    private static class Simple {
        int input = 0;

        int produce() {
            return ++input;
        }

        void consume(int v) {
            input = v;
        }

        int negate(int v) {
            input = v;
            return -v;
        }
    }

    @Test
    public void oneTask() throws Exception {
        CompletableFuture<Integer> cf = $.fn(() -> 1);
        int got = cf.get();
        assertEquals(1, got);
    }

    @Test
    public void oneTaskGet() throws Exception {
        int got = $.fn(() -> 1).get();
        assertEquals(1, got);
    }

    @Test
    public void oneTaskDelayedUntilGet() throws Exception {
        Simple simple = new Simple();
        CompletableFuture<Integer> cf = $.fn(simple::produce);
        assertEquals(1, simple.input);
        int got = cf.get();
        assertEquals(1, simple.input);
        assertEquals(1, got);
    }

    @Test
    public void twoTasks() throws Exception {
        CompletableFuture<Integer> t1 = $.fn(() -> 1);
        CompletableFuture<Integer> t2 = $.fn(t1, x -> x + 1);
        int got = t2.get();
        assertEquals(2, got);
    }

    @Test
    public void futureFunction() throws Exception {
        CompletableFuture<Integer> t1 = $.fn(() -> 3);
        CompletableFuture<Integer> task = $.fn(
                t1,
                x -> x * 2);

        int got = task.get();
        assertEquals(6, got);
    }

    @Test
    public void threeTasks() throws Exception {
        CompletableFuture<Integer> t1 = $.fnTask(() -> 2).name("task0").apply();
        CompletableFuture<Integer> t2 = $.fnTask(t1, x -> x + 3).name("task1").apply();
        CompletableFuture<Integer> t3 = $.fnTask(t1, t2, (x, y) -> x * y).name("task2").apply();
        int got = t3.get();
        assertEquals(10, got);
    }

    @Test
    public void supSupBiFunction() throws Exception {
        CompletableFuture<Integer> task = $.fn(
                () -> 2,
                () -> 3,
                (x, y) -> x * y);

        int got = task.get();
        assertEquals(6, got);
    }

    @Test
    public void supFutureBiFunction() throws Exception {
        CompletableFuture<Integer> t2 = $.fn(() -> 3);
        CompletableFuture<Integer> task = $.fn(
                () -> 2,
                t2,
                (x, y) -> x * y);

        int got = task.get();
        assertEquals(6, got);
    }

    @Test
    public void futureSupBiFunction() throws Exception {
        CompletableFuture<Integer> t1 = $.fn(() -> 2);
        CompletableFuture<Integer> task = $.fn(
                t1,
                () -> 3,
                (x, y) -> x * y);

        int got = task.get();
        assertEquals(6, got);
    }

    @Test
    public void futureSupBiConsumer() throws Exception {
        CompletableFuture<Integer> t1 = $.fn(() -> 2);
        Simple simple = new Simple();
        CompletableFuture<Void> task = $.vfn(
                t1,
                () -> 3,
                (x, y) -> simple.input = x + y);

        task.get();
        assertEquals(5, simple.input);
    }

    @Test
    public void supFutureBiConsumer() throws Exception {
        CompletableFuture<Integer> t2 = $.fn(() -> 3);
        Simple simple = new Simple();
        CompletableFuture<Void> task = $.vfn(
                () -> 2,
                t2,
                (x, y) -> simple.input = x + y);

        task.get();
        assertEquals(5, simple.input);
    }

    @Test
    public void futureFutureBiConsumer() throws Exception {
        CompletableFuture<Integer> t1 = $.fn(() -> 2);
        CompletableFuture<Integer> t2 = $.fn(() -> 3);
        Simple simple = new Simple();
        CompletableFuture<Void> task = $.vfn(
                t1,
                t2,
                (x, y) -> simple.input = x + y);

        task.get();
        assertEquals(5, simple.input);
    }

    @Test
    public void supSupBiConsumer() throws Exception {
        Simple simple = new Simple();
        CompletableFuture<Void> task = $.vfn(
                () -> 2,
                () -> 3,
                (x, y) -> simple.input = x + y);

        task.get();
        assertEquals(5, simple.input);
    }


    @Test
    public void taskAdaptor() throws Exception {
        int v = 5;
        CompletableFuture<Integer> cf = $.task(new Simple(), s -> s.negate(v));
        int got = cf.get();
        assertEquals(-v, got);
    }

    @Test
    public void taskAdaptorGet() throws Exception {
        int v = 5;
        CompletableFuture<Integer> cf = $.task(new Simple(), s -> s.negate(v));
        int got = cf.get();
        assertEquals(-v, got);
    }

    @Test
    public void taskAdaptorVoid() throws Exception {
        int v = 5;
        Simple simple = new Simple();
        CompletableFuture<Void> cf = $.voidTask(simple, s -> s.consume(v));
        assertEquals(0, simple.input);
        cf.get();
        assertEquals(v, simple.input);
    }

    @Test
    public void fnExecuteAndWait() {
        CompletableFuture<Integer> cf1 = $.task(task()).ret(1);
        CompletableFuture<Integer> cf2 = $.fn(cf1,x->x+1);
        //System.out.println(cf2.join());
        $.executeAndWait(cf1,cf2);
        System.out.println("E&W");
        assertEquals(Integer.valueOf(2),cf2.join());
    }
}

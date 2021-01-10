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

import com.ebay.bascomtask.annotations.Light;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Task with a mix of methods for various and diverse tests, with built-in ability to detect
 * not-enough or too many execution flows.
 *
 * @author Brendan Mccarthy
 */
public interface UberTask extends TaskInterface<UberTask> {
    /**
     * Enables regular vs. light calls to be made on methods of {@link UberTask}.
     */
    enum Weight {
        LIGHT,
        HEAVY;

        CompletableFuture<Integer> ret(UberTask t, Integer v) {
            return this == LIGHT ? t.retLight(v) : t.ret(v);
        }

        CompletableFuture<Integer> inc(UberTask t, CompletableFuture<Integer> cf) {
            return this == LIGHT ? t.incLight(cf) : t.inc(cf);
        }
    }

    String getThreadName();

    int nonFutureRet(CompletableFuture<Integer> cf);

    void voidConsume();

    void voidConsume(CompletableFuture<?> cf);

    CompletableFuture<Void> consume();

    CompletableFuture<Void> consume(CompletableFuture<?> cf);

    CompletableFuture<Integer> retValueOne();

    CompletableFuture<Integer> ret(int x);

    CompletableFuture<Integer> retLight(int x);

    CompletableFuture<Integer> inc(CompletableFuture<Integer> cf);

    CompletableFuture<Integer> incLight(CompletableFuture<Integer> cf);

    CompletableFuture<Integer> add(CompletableFuture<Integer> x, CompletableFuture<Integer> y);

    CompletableFuture<Integer> add(CompletableFuture<Integer> x, CompletableFuture<Integer> y, CompletableFuture<Integer> z);

    CompletableFuture<Integer> add(CompletableFuture<Integer> w, CompletableFuture<Integer> x, CompletableFuture<Integer> y, CompletableFuture<Integer> z);

    CompletableFuture<Integer> addb(CompletableFuture<Integer> w, CompletableFuture<Boolean> x, CompletableFuture<Boolean> y, CompletableFuture<Integer> z);

    CompletableFuture<Integer> incIf(CompletableFuture<Integer> x, CompletableFuture<Boolean> y);

    CompletableFuture<Boolean> ret(boolean b);

    CompletableFuture<Boolean> invert(CompletableFuture<Boolean> x);

    CompletableFuture<Integer> faultRecover(Orchestrator $, int x);

    boolean ranInSameThread(UberTask task);

    static void checkDone(CompletableFuture<?> cf) {
        //RunMode.mode.checkDone(cf);
        assertTrue("Input argument is not done", cf.isDone());
    }

    /**
     * Creates a task with a default expectedCount of 1.
     *
     * @return new task
     */
    static UberTasker task() {
        return new UberTasker();
    }

    /**
     * Creates a task that will fail junit assertion if not executed the given number of times.
     *
     * @return new task
     */
    static UberTasker task(int expectedCount) {
        return new UberTasker(expectedCount);
    }

    UberTasker delayFor(int ms);

    class UberTasker implements UberTask {
        private String threadName = null;
        private final int expectedExecutionCount;
        private int sleepForMs = 20;
        private final AtomicInteger actualCount = new AtomicInteger(0);

        private static final List<UberTasker> tasks = new ArrayList<>();

        int getActualCount() {
            return actualCount.get();
        }

        @Override
        public UberTasker delayFor(int ms) {
            sleepForMs = ms;
            return this;
        }

        static void clearAndVerify() {
            StringBuilder sb = null;
            for (UberTasker next : tasks) {
                if (next.actualCount.get() != next.expectedExecutionCount) {
                    if (sb == null) {
                        sb = new StringBuilder();
                    }
                    sb.append(String.format("Task(%s) got %d != (exp) %d%n",
                            next.getName(), next.actualCount.get(), next.expectedExecutionCount));
                }
            }
            tasks.clear();
            if (sb != null) {
                fail("Task-count mismatch: " + sb);
            }
        }

        UberTasker() {
            this(1);
        }

        /**
         * These test tasks are expected bo be executed once and only once, unless told
         * otherwise. The default count of execution passes can be passed here.
         *
         * @param expectedExecutionCount to expect
         */
        UberTasker(int expectedExecutionCount) {
            this.expectedExecutionCount = expectedExecutionCount;
            tasks.add(this);
        }

        public String getName() {
            int pos = tasks.indexOf(this);
            return "UberTask" + pos;
        }

        @Override
        public String getThreadName() {
            return threadName;
        }

        @Override
        public String toString() {
            return "UberTasker";
        }

        @Override
        public boolean ranInSameThread(UberTask that) {
            return Objects.equals(this.threadName, that.getThreadName());
        }

        private void delay() {
            threadName = Thread.currentThread().getName();
            if (sleepForMs > 0) {
                try {
                    Thread.sleep(sleepForMs);
                } catch (InterruptedException e) {
                    throw new TaskInterruptedException("Interrupted during delay");
                }
            }
            actualCount.incrementAndGet();
        }

        @Override
        public int nonFutureRet(CompletableFuture<Integer> cf) {
            delay();
            return get(cf);
        }

        @Override
        public void voidConsume() {
            delay();
        }

        @Override
        public void voidConsume(CompletableFuture<?> cf) {
            delay();
        }

        @Override
        public CompletableFuture<Void> consume() {
            delay();
            return complete();
        }

        @Override
        public CompletableFuture<Void> consume(CompletableFuture<?> cf) {
            delay();
            return complete();
        }

        @Override
        public CompletableFuture<Integer> retValueOne() {
            delay();
            return complete(1);
        }

        @Override
        public CompletableFuture<Integer> ret(int x) {
            delay();
            return complete(x);
        }

        @Override
        public CompletableFuture<Boolean> ret(boolean b) {
            delay();
            return complete(b);
        }

        @Override
        public CompletableFuture<Boolean> invert(CompletableFuture<Boolean> x) {
            Boolean b = get(x);
            delay();
            return complete(!b);
        }

        @Override
        @Light
        public CompletableFuture<Integer> retLight(int x) {
            return ret(x);
        }

        @Override
        public CompletableFuture<Integer> inc(CompletableFuture<Integer> cf) {
            checkDone(cf);
            int v = get(cf);
            delay();
            return complete(v + 1);
        }

        @Override
        @Light
        public CompletableFuture<Integer> incLight(CompletableFuture<Integer> cf) {
            checkDone(cf);
            return inc(cf);
        }

        @Override
        public CompletableFuture<Integer> add(CompletableFuture<Integer> fx, CompletableFuture<Integer> fy) {
            checkDone(fx);
            checkDone(fy);
            int x = get(fx);
            int y = get(fy);
            delay();
            return complete(x + y);
        }

        @Override
        public CompletableFuture<Integer> add(CompletableFuture<Integer> fx, CompletableFuture<Integer> fy, CompletableFuture<Integer> fz) {
            checkDone(fx);
            checkDone(fy);
            checkDone(fz);
            int x = get(fx);
            int y = get(fy);
            int z = get(fz);
            delay();
            return complete(x + y + z);
        }

        @Override
        public CompletableFuture<Integer> add(CompletableFuture<Integer> fw, CompletableFuture<Integer> fx, CompletableFuture<Integer> fy, CompletableFuture<Integer> fz) {
            checkDone(fw);
            checkDone(fx);
            checkDone(fy);
            checkDone(fz);
            int w = get(fw);
            int x = get(fx);
            int y = get(fy);
            int z = get(fz);
            delay();
            return complete(w + x + y + z);
        }

        @Override
        public CompletableFuture<Integer> addb(CompletableFuture<Integer> fw, CompletableFuture<Boolean> fx, CompletableFuture<Boolean> fy, CompletableFuture<Integer> fz) {
            checkDone(fw);
            checkDone(fx);
            checkDone(fy);
            checkDone(fz);
            int w = get(fw);
            int x = get(fx) ? 1 : 0;
            int y = get(fy) ? 1 : 0;
            int z = get(fz);
            delay();
            return complete(w + x + y + z);
        }

        @Override
        public CompletableFuture<Integer> incIf(CompletableFuture<Integer> x, CompletableFuture<Boolean> b) {
            checkDone(x);
            checkDone(b);
            int v = get(x);
            if (get(b)) {
                v++;
            }
            return complete(v);
        }

        @Override
        public CompletableFuture<Integer> faultRecover(Orchestrator $, int x) {
            ExceptionTask<Object> task = $.task(new ExceptionTask.Faulty<>());
            delay();
            try {
                get(task.faultImmediate("test"));
            } catch (ExceptionTask.FaultHappened e) {
                return complete(x);
            }
            throw new RuntimeException("Did not fault");
        }
    }
}

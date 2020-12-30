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
package com.ebay.bascomtask.runners;

import com.ebay.bascomtask.core.*;

import static com.ebay.bascomtask.core.UberTask.*;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests ProfilingTaskRunner.
 *
 * @author Brendan McCarthy
 */
public class ProfilingTaskRunnerTest extends BaseOrchestratorTest {

    private static class FakeTaskRun implements TaskRun {

        private final long startedAt;
        private final long endedAt;
        private final long completedAt;
        private final String taskName;
        private final String methodName;

        FakeTaskRun(long startedAt, long endedAt, long completedAt, String taskName, String methodName) {
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.completedAt = completedAt;
            this.taskName = taskName;
            this.methodName = methodName;
        }

        @Override
        public TaskInterface<?> getTask() {
            return null;
        }

        @Override
        public String getName() {
            return taskName;
        }

        @Override
        public String getTaskPlusMethodName() {
            return taskName + '.' + methodName;
        }

        @Override
        public void formatActualSignature(StringBuilder sb) {

        }

        @Override
        public Object run() {
            return null;
        }

        @Override
        public long getStartedAt() {
            return startedAt;
        }

        @Override
        public long getEndedAt() {
            return endedAt;
        }

        @Override
        public long getCompletedAt() {
            return completedAt;
        }
    }

    private ProfilingTaskRunner taskRunner;

    @Before
    public void before() {
        super.before();
        taskRunner = new ProfilingTaskRunner();
    }

    private void run(String threadName, long startedAt, long endedAt, String name, String method) {
        run(threadName,startedAt,endedAt,endedAt,name,method);
    }

    private void run(String threadName, long startedAt, long endedAt, long completedAt, String name, String method) {
        String orgName = Thread.currentThread().getName();
        Thread.currentThread().setName(threadName);
        Thread thread = Thread.currentThread();
        FakeTaskRun fakeRun = new FakeTaskRun(startedAt, endedAt, completedAt, name, method);
        try {
            taskRunner.executeTaskMethod(fakeRun,thread,null);
            taskRunner.onComplete(fakeRun,null,true);
        } finally {
            Thread.currentThread().setName(orgName);
        }
    }

    private static void log(Object x) {
        System.out.println(x);
    }

    private static final String T1 = "BLUE";
    private static final String T2 = "GREEN";
    private static final String T3 = "RED";

    @Test
    public void test1() {
        run(T1,1,10,"blue","dog");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("  0| blue.dog    ---"));
    }

    @Test
    public void testTwoSeq() {
        run(T1,0,10,"blue","dog");
        run(T1,10,20,"green","hornet");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("10| green.hornet    ---"));
    }

    @Test
    public void testTwoOverlap() {
        run(T1, 0,10,"blue","dog");
        run(T2, 7,17,"green","hornet");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("7| green.hornet     -  ---"));
        assertTrue(fmt.contains("17|                     ---"));
    }

    @Test
    public void testSeqAndOverlap() {
        run(T1, 0,10,"blue","dog");
        run(T2, 7,17,"green","hornet");
        run(T1, 10,17,"blue","pony");
        run(T2, 18,21,"green","bird");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains(" 10| blue.pony       ---  - "));
        assertTrue(fmt.contains(" 18| green.bird          ---"));
    }

    @Test
    public void testThree() {
        run(T1, 0,10,"blue","dog");
        run(T2, 7,17,"green","hornet");
        run(T1, 10,17,"blue","pony");
        run(T2, 18,21,"green","bird");
        run(T3, 3,21,"red","cat");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("7| green.hornet     -   -  ---"));
        assertTrue(fmt.contains("18| green.bird           -  ---"));
    }

    @Test
    public void test1xc() {
        run(T1,1,10,15,"blue","dog");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("  0| blue.dog    --- -+-"));
        assertTrue(fmt.contains("  9|             ---  + "));
    }

    @Test
    public void test2xc() {
        run(T1,0,10,15,"gold","pond");
        run(T1,10,20,25,"gold","fish");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("0| gold.fish    ---  +  -+-"));
    }

    @Test
    public void withEngine() throws Exception {

        ProfilingTaskRunner taskRunner = new ProfilingTaskRunner();

        $.firstInterceptWith(taskRunner);

        $.task(task()).name("blue").ret(1).get();

        String fmt = taskRunner.format();
        assertTrue(fmt.contains("0| blue.ret    ---"));
    }
}

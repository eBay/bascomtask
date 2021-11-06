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
package com.ebay.bascomtask.runners;

import com.ebay.bascomtask.core.*;

import static com.ebay.bascomtask.core.UberTask.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

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
        public boolean isLight() {
            return false;
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
        run(threadName, startedAt, endedAt, endedAt, name, method);
    }

    private void run(String threadName, long startedAt, long endedAt, long completedAt, String name, String method) {
        String orgName = Thread.currentThread().getName();
        Thread.currentThread().setName(threadName);
        Thread thread = Thread.currentThread();
        FakeTaskRun fakeRun = new FakeTaskRun(startedAt, endedAt, completedAt, name, method);
        try {
            taskRunner.executeTaskMethod(fakeRun, thread, null);
            taskRunner.onComplete(fakeRun, null, true);
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
        run(T1, 1, 10, "blue", "dog");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("  0| blue.dog    ---"));
    }

    @Test
    public void testTwoSeq() {
        run(T1, 0, 10, "blue", "dog");
        run(T1, 10, 20, "green", "hornet");
        String fmt = taskRunner.format();
        log(fmt);
        String exp = "                      0  \n";
        exp += "  0| blue.dog        --- \n";
        exp += " 10| green.hornet    =-= \n";
        exp += " 20|                 --- \n";
        assertEquals(exp, fmt);
    }

    @Test
    public void testTwoOverlap() {
        run(T1, 0, 10, "blue", "dog");
        run(T2, 7, 17, "green", "hornet");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("7| green.hornet     -  ---"));
        assertTrue(fmt.contains("17|                     ---"));
    }

    @Test
    public void testSeqAndOverlap() {
        run(T1, 0, 10, "blue", "dog");
        run(T2, 7, 17, "green", "hornet");
        run(T1, 10, 17, "blue", "pony");
        run(T2, 18, 21, "green", "bird");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains(" 10| blue.pony       =-=  - "));
        assertTrue(fmt.contains(" 18| green.bird          ---"));
    }

    @Test
    public void testThree() {
        run(T1, 0, 10, "blue", "dog");
        run(T2, 7, 17, "green", "hornet");
        run(T1, 10, 17, "blue", "pony");
        run(T2, 18, 21, "green", "bird");
        run(T3, 3, 21, "red", "cat");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("7| green.hornet     -   -  ---"));
        assertTrue(fmt.contains("18| green.bird           -  ---"));
    }

    @Test
    public void test1xc() {
        run(T1, 1, 10, 15, "blue", "dog");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("  0| blue.dog    --- -+-"));
        assertTrue(fmt.contains("  9|             ---  + "));
    }

    @Test
    public void test2xc() {
        run(T1, 0, 10, 15, "gold", "pond");
        run(T1, 10, 20, 25, "gold", "fish");
        String fmt = taskRunner.format();
        log(fmt);
        assertTrue(fmt.contains("0| gold.fish    =-=  +  -+-"));
    }

    @Test
    public void withEngine() throws Exception {

        ProfilingTaskRunner taskRunner = new ProfilingTaskRunner();

        assertEquals(0, $.getNumberOfInterceptors());

        $.firstInterceptWith(taskRunner);

        assertEquals(1, $.getNumberOfInterceptors());

        $.task(task()).name("blue").ret(1).get();

        String fmt = taskRunner.format();
        assertTrue(fmt.contains("0| blue.ret    ---"));
    }

    private final ThreadLocal<ProfilingTaskRunner> threadLocal = new ThreadLocal<>();

    @Test
    public void globalConfigReplace() throws Exception {
        GlobalOrchestratorConfig.setConfig(new GlobalOrchestratorConfig.Config() {
            @Override
            public void afterDefaultInitialization(Orchestrator orchestrator, Object arg) {
                ProfilingTaskRunner ptr = new ProfilingTaskRunner();
                threadLocal.set(ptr);
                orchestrator.firstInterceptWith(ptr);
            }
        });

        Orchestrator $ = Orchestrator.create();
        assertEquals(1, $.getNumberOfInterceptors());

        $.task(task()).name("single").ret(1).get();

        ProfilingTaskRunner ptr = threadLocal.get();
        String got = ptr.format();
        assertTrue(got.contains("0| single.ret    ---"));
    }

    private void expectInterceptors(int count, Orchestrator orchestrator, LaneRunner laneRunner) {
        assertEquals(1, orchestrator.getNumberOfInterceptors());
        assertEquals(count, laneRunner.runners.size());
    }

    @Test
    public void installNone() throws Exception {
        try (LaneRunner<ProfilingTaskRunner> laneRunner = GlobalOrchestratorConfig.interceptFirstOnCreate(ProfilingTaskRunner::new)) {
            assertEquals(0, laneRunner.runners.size());
        }
    }

    @Test
    public void installSingle() throws Exception {
        try (LaneRunner<ProfilingTaskRunner> laneRunner = GlobalOrchestratorConfig.interceptFirstOnCreate(ProfilingTaskRunner::new)) {
            Orchestrator $ = Orchestrator.create();
            expectInterceptors(1,$,laneRunner);;
            $.task(task()).name("single").ret(1).get();
            String got = laneRunner.runners.get(0).format();
            assertTrue(got.contains("0| single.ret"));
        }
    }

    @Test
    public void installMulti() throws Exception {
        try (LaneRunner<ProfilingTaskRunner> laneRunner = GlobalOrchestratorConfig.interceptLastOnCreate(ProfilingTaskRunner::new)) {
            Orchestrator $1 = Orchestrator.create();
            expectInterceptors(1,$1,laneRunner);;
            $1.task(task()).name("single").ret(1).get();
            Orchestrator $2 = Orchestrator.create();
            expectInterceptors(2,$2,laneRunner);;
            $2.task(task()).name("single").ret(1).get();
            String got1 = laneRunner.runners.get(0).format();
            assertTrue(got1.contains("0| single.ret"));
            String got2 = laneRunner.runners.get(1).format();
            assertTrue(got2.contains("0| single.ret"));
        }
    }

    @Test
    public void instalNested() throws Exception {
        try (LaneRunner<ProfilingTaskRunner> laneRunner1 = GlobalOrchestratorConfig.interceptLastOnCreate(ProfilingTaskRunner::new)) {
            try (LaneRunner<ProfilingTaskRunner> laneRunner2 = GlobalOrchestratorConfig.interceptLastOnCreate(ProfilingTaskRunner::new)) {
                Orchestrator $ = Orchestrator.create();
                assertEquals(2, $.getNumberOfInterceptors());
                assertEquals(1, laneRunner1.runners.size());
                assertEquals(1, laneRunner2.runners.size());
            }
            try (LaneRunner<ProfilingTaskRunner> laneRunner2 = GlobalOrchestratorConfig.interceptLastOnCreate(ProfilingTaskRunner::new)) {
                Orchestrator $ = Orchestrator.create();
                assertEquals(2, $.getNumberOfInterceptors());
                assertEquals(2, laneRunner1.runners.size());
                assertEquals(1, laneRunner2.runners.size());
            }
        }
    }
}

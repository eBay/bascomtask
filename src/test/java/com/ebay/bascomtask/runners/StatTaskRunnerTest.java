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

import com.ebay.bascomtask.core.BaseOrchestratorTest;
import com.ebay.bascomtask.core.GlobalConfig;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static com.ebay.bascomtask.core.UberTask.task;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests StatTaskRunner.
 *
 * @author Brendan McCarthy
 */
public class StatTaskRunnerTest extends BaseOrchestratorTest {

    private final static MockTaskRun R1 = new MockTaskRun("bear", "hibernate");
    private final static MockTaskRun R2 = new MockTaskRun("bear", "run");

    @Test
    public void empty() {
        StatTaskRunner runner = new StatTaskRunner();
        String report = runner.report();
        System.out.println(report);
        String exp = "------------------------------------\n"
                + "| Count | Avg | Min | Max | Method |\n"
                + "|-------|-----|-----|-----|--------|\n"
                + "------------------------------------\n";
        assertEquals(exp, report);
    }

    @Test
    public void oneRowOneTime() {
        StatTaskRunner runner = new StatTaskRunner();
        R1.sim(runner, 0, 10);

        String report = runner.report();
        System.out.println(report);
        String exp = "--------------------------------------------\n"
                + "| Count | Avg | Min | Max |     Method     |\n"
                + "|-------|-----|-----|-----|----------------|\n"
                + "|1      |10   |10   |10   |bear.hibernate  |\n"
                + "--------------------------------------------\n";
        assertEquals(exp, report);
    }

    @Test
    public void oneRowOneTimeExtended() {
        StatTaskRunner runner = new StatTaskRunner();
        R1.sim(runner, 0, 10, 12);

        String report = runner.report();
        System.out.println(report);
        assertTrue(report.contains("|1      |10+2  |10+2  |10+2  |bear.hibernate  |"));
    }

    @Test
    public void oneRowTwoTimes() {
        StatTaskRunner runner = new StatTaskRunner();
        R1.sim(runner, 0, 10);
        R1.sim(runner, 5, 45);

        String report = runner.report();
        System.out.println(report);
        assertTrue(report.contains("|2      |25   |10   |40   |bear.hibernate  |"));
    }

    @Test
    public void twoRowsOneTime() {
        StatTaskRunner runner = new StatTaskRunner();
        R1.sim(runner, 0, 10);
        R2.sim(runner, 5, 45);

        String report = runner.report();
        System.out.println(report);
        assertTrue(report.contains("|1      |10   |10   |10   |bear.hibernate  |"));
        assertTrue(report.contains("|1      |40   |40   |40   |bear.run        |"));
    }

    @Test
    public void oneRowManyTimes() {
        StatTaskRunner runner = new StatTaskRunner();
        for (int i = 0; i < 999999; i++) {
            long start = i % 100;
            long end = start + 1000 + i % 101;
            R1.sim(runner, start, end);
        }

        String report = runner.report();
        System.out.println(report);
        assertTrue(report.contains("|999999  |1050  |1000  |1100  |bear.hibernate  |"));
    }

    @Test
    public void oneRunner() throws Exception {
        StatTaskRunner taskRunner = new StatTaskRunner();
        GlobalConfig.INSTANCE.firstInterceptWith(taskRunner);
        for (int i = 0; i < 3; i++) {
            CompletableFuture<Integer> c = $.task(task().delayFor(30)).name("blue").ret(1);
            CompletableFuture<Integer> inc = $.task(task().delayFor(20)).name("red").inc(c);
            inc.get();
        }
        String report = taskRunner.report();
        System.out.println(report);
        assertTrue(report.contains("|3      |"));
        assertTrue(report.contains("|red.inc   |"));
        assertTrue(report.contains("|blue.ret  |"));
    }
}


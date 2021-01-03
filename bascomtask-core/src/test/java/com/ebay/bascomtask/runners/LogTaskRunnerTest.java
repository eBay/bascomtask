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

import org.junit.Test;

/**
 * Tests LogTaskRunner.
 *
 * @author Brendan McCarthy
 */
public class LogTaskRunnerTest {

    private final static MockTaskRun R1 = new MockTaskRun("the", "task");

    private void write(LogTaskRunner runner, LogTaskLevel level) {
        runner.setLevel(level);
        Object fromBefore = runner.before(R1);
        Thread thread = Thread.currentThread();
        runner.executeTaskMethod(R1, thread, fromBefore);
        runner.onComplete(R1, fromBefore, false);
        runner.onComplete(R1, fromBefore, true);
    }

    private void runAll(boolean full) {
        LogTaskRunner runner = new LogTaskRunner();
        runner.setFullSignatureLogging(full);
        for (LogTaskLevel next : LogTaskLevel.values()) {
            write(runner, next);
        }
    }

    @Test
    public void empty() {
        runAll(false);
        runAll(true);
    }
}


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

import com.ebay.bascomtask.core.TaskInterface;
import com.ebay.bascomtask.core.TaskRun;
import com.ebay.bascomtask.core.TaskRunner;

/**
 * Mocks TaskRun for testing TaskRunners.
 *
 * @author Brendan McCarthy
 */
public class MockTaskRun implements TaskRun {
    private final String name;
    private final String npm;
    private long startedAt;
    private long endedAt;
    private long completedAt;

    MockTaskRun(String name, String method) {
        this.name = name;
        this.npm = name + '.' + method;
    }

    void sim(TaskRunner taskRunner, long startedAt, long endedAt) {
        sim(taskRunner, startedAt, endedAt, endedAt);
    }

    void sim(TaskRunner taskRunner, long startedAt, long endedAt, long completedAt) {
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.completedAt = completedAt;
        Object fromBefore = taskRunner.before(this);
        taskRunner.executeTaskMethod(this, Thread.currentThread(), fromBefore);
        boolean doneOnExit = endedAt <= startedAt;
        taskRunner.onComplete(this, fromBefore, doneOnExit);
    }

    @Override
    public TaskInterface<?> getTask() {
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTaskPlusMethodName() {
        return npm;
    }

    @Override
    public void formatActualSignature(StringBuilder sb) {
        sb.append(npm);
        sb.append("()");
    }

    @Override
    public Object run() {
        return this; // doesn't matter
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

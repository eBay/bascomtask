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

/**
 * Maintains TaskRunners in a linked list, rather than requiring TaskRunners themselves
 * to maintain that list.
 *
 * @author Brendan McCarthy
 */
class PlaceHolderRunner implements TaskRun {
    private final Object fromBefore;
    private final Thread parentThread;
    private final TaskRun taskRun;
    private final TaskRunner next;

    PlaceHolderRunner(TaskRunner nextTaskRunner, Thread parentThread, TaskRun taskRun) {
        this.next = nextTaskRunner;
        this.parentThread = parentThread;
        this.taskRun = taskRun;
        this.fromBefore = next.before(taskRun);
    }

    @Override
    public String toString() {
        return "PRunner(" + taskRun + ")";
    }

    @Override
    public String getName() {
        return taskRun.getName();
    }

    @Override
    public String getTaskPlusMethodName() {
        return taskRun.getTaskPlusMethodName();
    }

    @Override
    public void formatActualSignature(StringBuilder sb) {
        taskRun.formatActualSignature(sb);
    }

    @Override
    public TaskInterface<?> getTask() {
        return taskRun.getTask();
    }

    @Override
    public Object run() {
        Object rv = next.executeTaskMethod(taskRun, parentThread, fromBefore);
        Binding.completeRunner(next, taskRun, fromBefore, rv);
        return rv;
    }

    @Override
    public long getStartedAt() {
        return taskRun.getStartedAt();
    }

    @Override
    public long getEndedAt() {
        return taskRun.getEndedAt();
    }

    @Override
    public long getCompletedAt() {
        return taskRun.getCompletedAt();
    }
}

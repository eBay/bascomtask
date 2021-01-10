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

/**
 * User task wrapper exposed to {@link TaskRunner}s.
 *
 * @author Brendan McCarthy
 */
public interface TaskRun extends TaskMeta {
    /**
     * Returns the user task pojo if there is one. For some internal tasks, there may not be.
     *
     * @return possibly null userTask
     */
    TaskInterface<?> getTask();

    /**
     * Returns the task name, e.g. for a user task TASK.METHOD, where TASK is the the user-specified task
     * renaming else the class name if none.
     *
     * @return never null name
     */
    String getName();

    String getTaskPlusMethodName();

    void formatActualSignature(StringBuilder sb);

    Object run();
}

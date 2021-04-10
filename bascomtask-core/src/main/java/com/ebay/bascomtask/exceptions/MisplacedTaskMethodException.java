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
package com.ebay.bascomtask.exceptions;

import com.ebay.bascomtask.core.TaskInterface;

/**
 * Thrown when a {@link com.ebay.bascomtask.core.TaskInterface} method is called directly on a user POJO
 * rather than the wrapper return from {@link com.ebay.bascomtask.core.Orchestrator#task(TaskInterface)},
 * when the user POJO has not overridden such methods (which is usually the case).
 *
 * @author Brendan McCarthy
 */
public class MisplacedTaskMethodException extends RuntimeException {

    public MisplacedTaskMethodException(TaskInterface<?> task, String method) {
        super(format(task,method));
    }

    private static String format(TaskInterface<?> task, String method) {
        return String.format(
                "Method %s.%s not overwritten in task subclass, so this method can only be applied on"
                + " task wrappers obtained from Orchestrator.task()",
                task.getClass().getSimpleName(),method);
    }
}

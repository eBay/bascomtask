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

import com.ebay.bascomtask.core.TaskRun;
import com.ebay.bascomtask.core.TaskRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Logs task execution before and after, with a configurable level and detail. A completion message is
 * also logged if the completion (of a CompletableFuture) occurs after message exit.
 *
 * @author Brendan McCarthy
 */
public class LogTaskRunner implements TaskRunner {
    private static final Logger LOG = LoggerFactory.getLogger(LogTaskRunner.class);

    private LogTaskLevel level = LogTaskLevel.DEBUG;
    private boolean full = false;

    /**
     * Set debug level. This does not configure the level, which must be done in any slf4j-conformant manner.
     * @param level to use
     */
    public void setLevel(LogTaskLevel level) {
        this.level = level;
    }

    /**
     * Makes log output include actual method arguments.
     * @param full include method arguments or not
     */
    public void setFullSignatureLogging(boolean full) {
        this.full = full;
    }

    private String detail(TaskRun taskRun) {
        if (full) {
            StringBuilder sb = new StringBuilder();
            taskRun.formatActualSignature(sb);
            return sb.toString();
        } else {
            return taskRun.getTaskPlusMethodName();
        }
    }

    @Override
    public Object before(TaskRun taskRun) {
        return level.isEnabled(LOG) ? detail(taskRun) : null;
    }

    @Override
    public Object executeTaskMethod(TaskRun taskRun, Thread parentThread, Object fromBefore) {
        final String state = parentThread != Thread.currentThread() ? "(spawned)" : "";
        if (level.isEnabled(LOG)) {
            level.write(LOG, "BEGIN-TASK {} {}", fromBefore, state);
        }
        try {
            return taskRun.run();
        } finally {
            if (level.isEnabled(LOG)) {
                level.write(LOG, "END-TASK {} {}", fromBefore, state);
            }
        }
    }

    @Override
    public void onComplete(TaskRun taskRun, Object fromBefore, boolean doneOnExit) {
        if (level.isEnabled(LOG) && !doneOnExit) {
            level.write(LOG,"COMPLETE-TASK {}",fromBefore);
        }
    }
}

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
 * Exposes execution statistics to {@link TaskRunner}s.
 *
 * @author Brendan McCarthy
 */
public interface TaskMeta {

    /**
     * Returns the time at which this task method was started.
     *
     * @return start time in ms or 0 if not started
     */
    long getStartedAt();

    /**
     * Returns the time at which this task method was ended. This value would always be greater
     * than or equal to {@link #getStartedAt()}.
     *
     * @return end time in ms or 0 if not ended
     */
    long getEndedAt();

    /**
     * Returns the time at which this task method was completed, which for CompletableFutures is the time
     * at which it had a value assigned. This value is always greater than or equal to {@link #getEndedAt()},
     * and is greater than the ending time iff the CompletableFuture had !isDone() on method exit.
     *
     * @return completion time in ms or 0 if not ended
     */
    long getCompletedAt();

    default boolean completedBefore(TaskMeta that) {
        // Since we only measure at ms granularity, have to assume that <= is <
        return this.getEndedAt() <= that.getStartedAt();
    }

    default boolean overlapped(TaskMeta that) {
        if (this.getStartedAt() > that.getEndedAt()) return false;
        if (this.getEndedAt() < that.getStartedAt()) return false;
        return true;
    }
}

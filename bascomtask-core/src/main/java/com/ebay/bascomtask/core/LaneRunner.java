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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Establishes a TaskRunner creation capability that for each new {@link Orchestrator} created in this thread
 * will be applied and recorded for later retrieval.
 *
 * @author bremccarthy
 * @param <T> type of TaskRunner to create
 * @see GlobalOrchestratorConfig#interceptFirstOnCreate(Supplier)
 */
public class LaneRunner<T extends TaskRunner> implements AutoCloseable {

    /**
     * Exposes all TaskRunners created by the thread that invoked the constructor, up until the time this LaneRunner
     * was closed (if it has been closed). This will usually be just zero or one, but implementations are free to
     * create as many orchestrators as desired. Beware that orchestrators created in spawned threads will not be
     * visible to this instance.
     *
     * <p>Care should be taken if this list is retrieved asynchronously since the list might still be added to.
     * A safe strategy is to retrieve the list at a point when all processing for this thread is completed. Otherwise,
     * the standard Java practices around avoiding ConcurrentModificationExceptions when traversing lists should be practiced.
     */
    public final List<T> runners = Collections.synchronizedList(new ArrayList<>());

    private static final ThreadLocal<LaneRunner<?>> threadLocal = new ThreadLocal<>();
    private final Supplier<T> createFn;
    private final boolean firstElseLast;
    private LaneRunner<?> previous = null;  // Non-null when nested

    LaneRunner(Supplier<T> createFn, boolean firstElseLast) {
        this.createFn = createFn;
        previous = threadLocal.get();
        threadLocal.set(this);
        this.firstElseLast = firstElseLast;
    }

    static void apply(Orchestrator orchestrator) {
        LaneRunner<?> laneRunner = threadLocal.get();
        intercept(orchestrator,laneRunner);
    }

    private static void intercept(Orchestrator orchestrator, LaneRunner<?> laneRunner) {
        if (laneRunner != null) {
            TaskRunner taskRunner = laneRunner.add();
            if (laneRunner.firstElseLast) {
                orchestrator.firstInterceptWith(taskRunner);
            } else {
                orchestrator.lastInterceptWith(taskRunner);
            }
            intercept(orchestrator,laneRunner.previous);
        }
    }

    private T add() {
        T taskRunner = createFn.get();
        runners.add(taskRunner);
        return taskRunner;
    }

    @Override
    public void close() throws Exception {
        if (previous==null) {
            threadLocal.remove();
        } else {
            threadLocal.set(previous);
        }
    }
}

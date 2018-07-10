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
package com.ebay.bascomtask.main;

/**
 * Wrapper for threads used during orchestrator execution. The threads are not
 * necessarily owned by the orchestrator since any shared executor pool may be
 * provided to an orchestrator during configuration.
 * 
 * @author bremccarthy
 * @see com.ebay.bascomtask.config.IBascomConfig#getExecutor()
 */
public class TaskThreadStat {
    private final Orchestrator orc;
    private final int localIndex;
    private final int globalIndex;
    private final TaskThreadStat root;
    private boolean active = false;

    // Scope is protected rather than package for testing purposes only
    protected TaskThreadStat(Orchestrator orc, int localIndex, int globalIndex, TaskThreadStat root) {
        this.orc = orc;
        this.localIndex = localIndex;
        this.globalIndex = globalIndex;
        this.root = root;
    }

    /**
     * The creator of this TaskThreadStat
     * 
     * @return never-null orchestrator
     */
    public Orchestrator getOrchestrator() {
        return orc;
    }

    /**
     * Returns the index of the associated thread within
     * {@link #getOrchestrator()}. The returned value will not be unique across
     * orchestrators.
     * 
     * @return locally unique index
     */
    public int getLocalIndex() {
        return localIndex;
    }

    /**
     * Returns the index of the associated thread within this process. The
     * returned value is unique across all orchestrators within this process.
     * 
     * @return globally unique index, or 0 for thread which calls
     *         {@link com.ebay.bascomtask.main.Orchestrator#execute()}
     */
    public int getGlobalIndex() {
        return globalIndex;
    }

    /**
     * Returns parent thread which created this thread.
     * 
     * @return parent TaskThreadStat
     */
    public TaskThreadStat getRoot() {
        return root;
    }

    /**
     * True iff the associated thread is actively executing a task.
     * 
     * @return true iff active
     */
    public boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }
}
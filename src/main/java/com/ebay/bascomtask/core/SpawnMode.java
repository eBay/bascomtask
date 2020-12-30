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
 * Defines thread spawning behavior for the BascomTask framework.
 *
 * @author Brendan McCarthy
 */
public enum SpawnMode {
    /**
     * Spawn whenever more than one task can be started at the same time. Default behavior.
     */
    WHEN_NEEDED(true),
    /**
     * Like {@link #WHEN_NEEDED}, but avoids any attempt to reuse main thread for processing.
     * That 'reuse' occurs when the main thread is sitting idle with no work to do while
     * spawned threads need further spawn threads.
     */
    WHEN_NEEDED_NO_REUSE(false),
    /**
     * Avoid using main (calling thread) to execute task methods, spawning threads when
     * the main thread is executing unless the task methods are marked as 'light'.
     */
    NEVER_MAIN(false),
    /**
     * Always spawn except for 'light' task methods. This is a stronger assertion then
     * {@link #NEVER_MAIN}, as the main thread will similarly not execute any tasks.
     */
    ALWAYS_SPAWN(false),
    /**
     * Never spawn under any circumstance.
     */
    NEVER_SPAWN(false),
    /**
     * Don't spawn unless a {@link TaskInterface#runSpawned()} request is made on a task.
     */
    DONT_SPAWN_UNLESS_EXPLICIT(true);

    private final boolean mainThreadReusable;

    SpawnMode(boolean mainThreadReusable) {
        this.mainThreadReusable = mainThreadReusable;
    }

    /**
     * Returns true if this mode allows for the main thread can be picked up while idle and otherwise
     * waiting for a CompletableFuture to complete, and used to to process spawning task methods.
     *
     * @return true iff main thread is reusable
     */
    public boolean isMainThreadReusable() {
        return mainThreadReusable;
    }

    public interface SpawnModable {
        SpawnMode getSpawnMode();

        void setSpawnMode(SpawnMode spawnMode);
    }
}

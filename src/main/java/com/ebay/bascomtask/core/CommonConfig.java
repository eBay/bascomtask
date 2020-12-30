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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Configuration settings. {@link GlobalConfig} and {@link Orchestrator} both implement this class, allowing
 * these values to be set at either level. In general if an Orchestrator has not set a value then the globally
 * configured value is used.
 *
 * @author Brendan McCarthy
 */
public interface CommonConfig {

    /**
     * Restore default settings. Applying either globally or locally only affects that level and not the other.
     */
    default void restoreDefaults() {
        setSpawnMode(null); // Same as SpawnMode.WHEN_NEEDED
        setTimeoutMs(0);
        removeAllTaskRunners();
        restoreDefaultExecutorService();
    }

    /**
     * Gets the most recently set mode. The global default is {@link SpawnMode#WHEN_NEEDED}. The Orchestrator
     * default is null, which results in using the global setting.
     * @return most recently-set mode (possibly null) or default if none set
     */
    SpawnMode getSpawnMode();

    /**
     * Changes the thread-spawning logic to be consistent with the specified mode. If not set at a more
     * specific level (Orchestrator), the globally-set value is used.
     * @param mode to set
     */
    void setSpawnMode(SpawnMode mode);

    /**
     * Gets the current timeout, which defaults to meaning no timeout.
     * @return current timeout
     */
    long getTimeoutMs();

    /**
     * Sets the current timeout. When set to a value greater than zero, the calling thread will wait and all
     * task execution will be done by spawned threads. The default is zero, meaning there will be no timeouts.
     * @param ms duration in milliseconds, zero means no timeout will be applied
     */
    void setTimeoutMs(long ms);

    /**
     * Calls {@link #setTimeoutMs(long)} after calculating the millisecond duration from the supplied arguments.
     * @param duration to timeout after
     * @param timeUnit to apply
     */
    default void setTimeout(long duration, TimeUnit timeUnit) {
        setTimeoutMs(timeUnit.toMillis(duration));
    }

    /**
     * Resets the service used by this framework for spawning threads. Global default is
     * Executors.newFixedThreadPool({@link GlobalConfig#DEFAULT_FIXED_THREADPOOL_SIZE}.
     * @param executorService to set as new default
     */
    void setExecutorService(ExecutorService executorService);

    void restoreDefaultExecutorService();

    /**
     * Adds a TaskRunner that will be processed before any existing TaskRunner, excepting
     * that all global runners are processed before local (orchestrator-specific) runners.
     *
     * @param taskRunner to add
     */
    void firstInterceptWith(TaskRunner taskRunner);

    /**
     * Adds a TaskRunner that will be processed after any existing TaskRunner, excepting
     * that all global runners are processed before local (orchestrator-specific) runners.
     * @param taskRunner to add
     */
    void lastInterceptWith(TaskRunner taskRunner);

    /**
     * Removes the given TaskRunner from the list, if it is already in the list.
     * @param taskRunner to remove
     */
    void removeInterceptor(TaskRunner taskRunner);

    /**
     * Removes all GlobalConfig from the list.
     */
    void removeAllTaskRunners();
}

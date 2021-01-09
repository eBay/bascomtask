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
 * Configuration settings. {@link GlobalOrchestratorConfig} and {@link Orchestrator} both implement this class, allowing
 * these values to be set at either level. On its creation, an Orchestrator's values are set from the current
 * global settings.
 *
 * @author Brendan McCarthy
 */
public interface CommonConfig {

    /**
     * Restore default settings. Applied on GlobalOrchestratorConfig, sets default values. Applied on Orchestrator,
     * sets values from current GlobalOrchestratorConfig.
     */
    void restoreConfigurationDefaults(Object arg);

    /**
     * Gets the most recently set mode. The global default is {@link SpawnMode#WHEN_NEEDED}. The Orchestrator
     * default is null, which results in using the global setting.
     *
     * @return most recently-set mode (possibly null) or default if none set
     */
    SpawnMode getSpawnMode();

    /**
     * Changes the thread-spawning logic to be consistent with the specified mode. If not set at a more
     * specific level (Orchestrator), the globally-set value is used.
     *
     * @param mode to set
     */
    void setSpawnMode(SpawnMode mode);

    /**
     * Gets the current timeout. The default is zero, meaning no timeout is in effect.
     *
     * @return current timeout
     */
    long getTimeoutMs();

    /**
     * Sets the current timeout. When set to a value greater than zero, the behavior defined by the current
     * {@link #getTimeoutStrategy()} will apply.
     *
     * @param ms duration in milliseconds, zero means no timeout will be applied
     */
    void setTimeoutMs(long ms);

    /**
     * Calls {@link #setTimeoutMs(long)} after calculating the millisecond duration from the supplied arguments.
     *
     * @param duration to timeout after
     * @param timeUnit to apply
     */
    default void setTimeout(long duration, TimeUnit timeUnit) {
        setTimeoutMs(timeUnit.toMillis(duration));
    }

    /**
     * Gets the current timeout strategy, default is {@link TimeoutStrategy#PREVENT_NEW}.
     *
     * @return default or strategy last set by {@link #setTimeoutStrategy(TimeoutStrategy)}
     */
    TimeoutStrategy getTimeoutStrategy();

    /**
     * Sets the strategy to apply if a timeout greater than zero is in effect and is exceeded.
     *
     * @param strategy to set
     */
    void setTimeoutStrategy(TimeoutStrategy strategy);

    ExecutorService getExecutorService();

    /**
     * Resets the service used by this framework for spawning threads. Global default is
     * Executors.newFixedThreadPool({@link GlobalOrchestratorConfig#DEFAULT_FIXED_THREADPOOL_SIZE}.
     *
     * @param executorService to set as new default
     */
    void setExecutorService(ExecutorService executorService);

    void restoreDefaultExecutorService();

    /**
     * Adds a TaskRunner that will be processed before any existing TaskRunner.
     *
     * @param taskRunner to add
     */
    void firstInterceptWith(TaskRunner taskRunner);

    /**
     * Adds a TaskRunner that will be processed after any existing TaskRunner.
     *
     * @param taskRunner to add
     */
    void lastInterceptWith(TaskRunner taskRunner);

    /**
     * Returns the current number of interceptors, as set by {@link #firstInterceptWith(TaskRunner)},
     * {@link #lastInterceptWith(TaskRunner)}, or for Orchestrators as set from current global settings.
     *
     * @return interceptor count
     */
    int getNumberOfInterceptors();

    /**
     * Removes the given TaskRunner from the current list, if it is there. It will exit silently if not there.
     *
     * @param taskRunner to remove
     */
    void removeInterceptor(TaskRunner taskRunner);

    /**
     * Removes all interceptors from the current list.
     */
    void removeAllTaskRunners();
}

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

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Global configuration for BascomTask. Values set here through the single {@link #INSTANCE}
 * apply to all {@link Orchestrator}s.
 *
 * @author Brendan McCarthy
 */
public class GlobalConfig implements CommonConfig, SpawnMode.SpawnModable {
    public final static int DEFAULT_FIXED_THREADPOOL_SIZE = 20;

    private static final ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newFixedThreadPool(DEFAULT_FIXED_THREADPOOL_SIZE);
    ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;

    final LinkedList<TaskRunner> globalRunners = new LinkedList<>();

    private SpawnMode spawnMode = SpawnMode.WHEN_NEEDED;
    private long timeoutMs = 0; // zero means no timeout

    public static final GlobalConfig INSTANCE = new GlobalConfig();

    private GlobalConfig() {
    }

    @Override
    public SpawnMode getSpawnMode() {
        return spawnMode;
    }

    @Override
    public void setSpawnMode(SpawnMode mode) {
        if (mode == null) {
            mode = SpawnMode.WHEN_NEEDED;
        }
        this.spawnMode = mode;
    }

    @Override
    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public void setTimeoutMs(long ms) {
        this.timeoutMs = ms;
    }

    @Override
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void restoreDefaultExecutorService() {
        setExecutorService(DEFAULT_EXECUTOR_SERVICE);
    }

    @Override
    public void firstInterceptWith(TaskRunner taskRunner) {
        globalRunners.addFirst(taskRunner);
    }

    @Override
    public void lastInterceptWith(TaskRunner taskRunner) {
        globalRunners.addLast(taskRunner);
    }

    @Override
    public void removeInterceptor(TaskRunner taskRunner) {
        globalRunners.remove(taskRunner);
    }

    @Override
    public void removeAllTaskRunners() {
        globalRunners.clear();
    }
}

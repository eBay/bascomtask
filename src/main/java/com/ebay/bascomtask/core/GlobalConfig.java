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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Maintains configuration settings that will be applied to new Orchestrators.
 *
 * @author Brendan McCarthy
 */
public class GlobalConfig {
    public final static int DEFAULT_FIXED_THREADPOOL_SIZE = 20;
    private static final ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newFixedThreadPool(DEFAULT_FIXED_THREADPOOL_SIZE);

    private static final Config DEFUALT_CONFIG = new Config() {
        public void afterDefaultInitialization(Orchestrator orchestrator, Object arg) {
        }
    };

    private static Config globalConfig = DEFUALT_CONFIG;


    /**
     * Default implementation maintains values that are transferred to an Orchestrator on demand.
     */
    public abstract static class Config implements CommonConfig {
        protected ExecutorService executorService;
        protected final List<TaskRunner> first = new ArrayList<>();
        protected final List<TaskRunner> last = new ArrayList<>();
        protected SpawnMode spawnMode;
        protected long timeoutMs;

        protected Config() {
            restoreConfigurationDefaults(null);
        }

        /**
         * Transfers configuration settings to the supplied orchestrator.
         *
         * @param orchestrator to update
         * @param arg          passed from user code, see {@link #afterDefaultInitialization(Orchestrator, Object)}
         */
        final public void updateConfigurationOn(Orchestrator orchestrator, Object arg) {
            orchestrator.setSpawnMode(getSpawnMode());
            orchestrator.setTimeoutMs(getTimeoutMs());
            orchestrator.setExecutorService(getExecutorService());
            for (TaskRunner next : first) {
                orchestrator.firstInterceptWith(next);
            }
            for (TaskRunner next : last) {
                orchestrator.lastInterceptWith(next);
            }
            afterDefaultInitialization(orchestrator, arg);
        }

        /**
         * Subclasses can override to provide custom logic for {@link #updateConfigurationOn(Orchestrator, Object)}.
         * The arg parameter is what is passed to {@link Orchestrator#create(String, Object)}, without modification.
         * It is intended to allow for this method to know to apply different settings for different orchestrators,
         * if desired.
         *
         * @param orchestrator to update
         * @param arg          passed from user code
         */
        abstract public void afterDefaultInitialization(Orchestrator orchestrator, Object arg);

        @Override
        public final void restoreConfigurationDefaults(Object arg) {
            globalConfig = DEFUALT_CONFIG;
            setSpawnMode(SpawnMode.WHEN_NEEDED);
            setTimeoutMs(0);
            removeAllTaskRunners();
            restoreDefaultExecutorService();
        }

        @Override
        public SpawnMode getSpawnMode() {
            return spawnMode;
        }

        @Override
        public void setSpawnMode(SpawnMode mode) {
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
        public ExecutorService getExecutorService() {
            return executorService;
        }

        @Override
        public void setExecutorService(ExecutorService executorService) {
            this.executorService = executorService;
        }

        @Override
        public void restoreDefaultExecutorService() {
            this.executorService = DEFAULT_EXECUTOR_SERVICE;
        }

        @Override
        public void firstInterceptWith(TaskRunner runner) {
            first.add(runner);
        }

        @Override
        public void lastInterceptWith(TaskRunner runner) {
            last.add(runner);
        }

        @Override
        public int getNumberOfInterceptors() {
            return first.size() + last.size();
        }

        @Override
        public void removeInterceptor(TaskRunner taskRunner) {
            first.remove(taskRunner);
            last.remove(taskRunner);
        }

        @Override
        public void removeAllTaskRunners() {
            first.clear();
            last.clear();
        }
    }

    public static Config getConfig() {
        return globalConfig;
    }

    public static void setConfig(Config config) {
        globalConfig = config;
    }
}

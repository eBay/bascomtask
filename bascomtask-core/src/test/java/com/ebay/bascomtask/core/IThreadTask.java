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

import com.ebay.bascomtask.annotations.Light;

import java.util.concurrent.CompletableFuture;

/**
 * A task with methods that are with/without a <code>Light</code> records whether its methods are executed,
 * and whose methods return the thread that executes them.
 *
 * @author Brendan McCarthy
 */
interface IThreadTask extends TaskInterface<IThreadTask> {
    CompletableFuture<Thread> computeAnnotated();

    CompletableFuture<Thread> computeNotAnnotated();

    class ThreadTask implements IThreadTask {
        private boolean defaultLight = false;
        private boolean defaultRunSpawned = false;
        boolean calledAnnotated = false;
        boolean calledNotAnnotated = false;

        @Override
        public ThreadTask light() {
            defaultLight = true;
            return this;
        }

        @Override
        public boolean isLight() {
            return defaultLight;
        }

        @Override
        public ThreadTask runSpawned() {
            defaultRunSpawned = true;
            return this;
        }

        @Override
        public boolean isRunSpawned() {
            return defaultRunSpawned;
        }

        @Override
        @Light
        public CompletableFuture<Thread> computeAnnotated() {
            calledAnnotated = true;
            return complete(Thread.currentThread());
        }

        @Override
        public CompletableFuture<Thread> computeNotAnnotated() {
            calledNotAnnotated = true;
            return complete(Thread.currentThread());
        }
    }
}

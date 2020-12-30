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
 * Task with naming subclasses employing different naming strategies.
 *
 * @author Brendan Mccarthy
 */
interface NamingTask extends TaskInterface<NamingTask> {

    class OverridesNothing implements NamingTask {

    }

    class OverridesGet implements NamingTask {
        @Override
        public String getName() {
            return "any_name_will_do";
        }
    }

    class OverridesGetAndSet implements NamingTask {
        private String name;
        @Override
        public String getName() {
            return name;
        }
        @Override
        public NamingTask name(String name) {
            this.name = name;
            return this;
        }
    }
}

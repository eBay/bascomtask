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

import java.util.concurrent.CompletableFuture;

/**
 * Variations combinations of task hierarchies.
 *
 * @author Brendan Mccarthy
 */
public class TaskVariations {
    static class Base {
        public CompletableFuture<String> ret(String s) {
            return CompletableFuture.completedFuture(s);
        }
    }

    interface NoTask {

    }

    static class No implements NoTask {
    }

    interface YesTask extends TaskInterface<YesTask> {
        CompletableFuture<String> ret(String s);
    }

    static class Yes extends Base implements YesTask {

    }

    interface SubYesTask  extends YesTask {
    }

    static class SubYes extends Base implements SubYesTask {
    }

    interface YesTaskP<TYPE> extends TaskInterface<YesTaskP<TYPE>> {
        CompletableFuture<TYPE> returnType(TYPE v);
    }

    static class YesP<TYPE> extends Base implements YesTaskP<TYPE> {
        public CompletableFuture<TYPE> returnType(TYPE v) {
            return CompletableFuture.completedFuture(v);
        }
    }

    interface YesTaskBefore  extends NoTask, TaskInterface<YesTaskBefore> {
        CompletableFuture<String> ret(String s);
    }

    static class YesBefore extends Base implements YesTaskBefore {
    }

    interface YesTaskAfter extends TaskInterface<YesTaskAfter>, NoTask {
        CompletableFuture<String> ret(String s);
    }

    static class YesAfter extends Base implements YesTaskAfter {
    }

    interface YesAndNoTask  extends NoTask, YesTask {

    }

    static class YesAndNo  extends Base implements YesAndNoTask {
    }

    static class YesAndNoClass extends Base implements NoTask, YesTask {
    }

    static class YesAndNoSubClass extends YesAndNoClass {
    }

    static class NoClassYes extends No implements YesTask {
        @Override
        public CompletableFuture<String> ret(String s) {
            return CompletableFuture.completedFuture(s);
        }
    }
}

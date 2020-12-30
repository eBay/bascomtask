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
package com.ebay.bascomtask;

import com.ebay.bascomtask.core.*;
import com.ebay.bascomtask.runners.LogTaskRunnerTest;
import com.ebay.bascomtask.runners.ProfilingTaskRunnerTest;
import com.ebay.bascomtask.runners.StatTaskRunnerTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        UtilsTest.class,
        CoreTest.class,
        BascomTaskFutureTest.class,
        FnTaskTest.class,
        TaskRunnerTest.class,
        LogTaskRunnerTest.class,
        ProfilingTaskRunnerTest.class,
        StatTaskRunnerTest.class
})
public class FullTestSuite {
}

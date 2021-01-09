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

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseOrchestratorTest {
    private static final Logger LOG = LoggerFactory.getLogger("@Test");

    protected Orchestrator $;

    @Rule
    public final TestName name = new TestName();

    @Before
    public void before() {
        GlobalOrchestratorConfig.getConfig().restoreConfigurationDefaults(null);
        $ = Orchestrator.create();
        String testName = name.getMethodName();
        LOG.debug(testName);
    }

    @Before
    public void after() {
        GlobalOrchestratorConfig.getConfig().restoreConfigurationDefaults(null);
    }

    static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException("Bad sleep", e);
        }
    }

    static int sleepThen(int ms, int v) {
        sleep(ms);
        return v;
    }
}

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

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;

/**
 * Utilities test.
 *
 * @author Brendan McCarthy
 */
public class UtilsTest extends BaseOrchestratorTest {

    @Test
    public void format() {
        final String base = "BASE";
        StringBuilder sb = new StringBuilder();
        Object[] args = {
                3,
                "foo",
                CompletableFuture.completedFuture(7),
                CompletableFuture.supplyAsync(() -> sleepThen(40, 9))
        };
        Utils.formatFullSignature(sb, base, args);
        assertEquals(base + "(3,foo,+7,-)", sb.toString());
    }
}

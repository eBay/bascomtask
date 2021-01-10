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

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static com.ebay.bascomtask.core.TaskVariations.*;
import static org.junit.Assert.assertEquals;

/**
 * Core BascomTask execution tests. These should be runnable before anything test files.
 *
 * @author Brendan McCarthy
 */
public class TaskVariationsTest extends BaseOrchestratorTest {

    private final static String SV = "xyz";

    @Test
    public void yes() throws Exception {
        CompletableFuture<String> s = $.task(new Yes()).ret(SV);
        assertEquals(SV, s.get());
    }

    @Test
    public void yesp() throws Exception {
        CompletableFuture<String> s = $.task(new YesP<String>()).returnType(SV);
        assertEquals(SV, s.get());
    }

    @Test
    public void subYes() throws Exception {
        CompletableFuture<String> s = $.task(new SubYes()).ret(SV);
        assertEquals(SV, s.get());
    }

    @Test
    public void yesBefore() throws Exception {
        CompletableFuture<String> s = $.task(new YesBefore()).ret(SV);
        assertEquals(SV, s.get());
    }

    @Test
    public void yesAfter() throws Exception {
        CompletableFuture<String> s = $.task(new YesAfter()).ret(SV);
        assertEquals(SV, s.get());
    }

    @Test
    public void yesAndNo() throws Exception {
        CompletableFuture<String> s = $.task(new YesAndNo()).ret(SV);
        assertEquals(SV, s.get());
    }

    @Test
    public void yesAndNoClass() throws Exception {
        CompletableFuture<String> s = $.task(new YesAndNoClass()).ret(SV);
        assertEquals(SV, s.get());
    }

    @Test
    public void yesAndNoSubClass() throws Exception {
        CompletableFuture<String> s = $.task(new YesAndNoSubClass()).ret(SV);
        assertEquals(SV, s.get());
    }

    @Test
    public void noClassYes() throws Exception {
        CompletableFuture<String> s = $.task(new NoClassYes()).ret(SV);
        assertEquals(SV, s.get());
    }
}

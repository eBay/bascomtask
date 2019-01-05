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
package com.ebay.bascomtask.main;

import static org.junit.Assert.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.ebay.bascomtask.annotations.Ordered;
import com.ebay.bascomtask.annotations.PassThru;
import com.ebay.bascomtask.annotations.Scope;
import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;
import com.ebay.bascomtask.exceptions.RuntimeGraphError;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for bindings variations
 * 
 * @author brendanmccarthy
 */
@SuppressWarnings("unused")
@SuppressFBWarnings("UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS") // Simplistic rule misses valid usage
public class BindingsTest extends PathTaskTestBase {

    @Test
    public void testOneReturn() {
        final String r1 = "r1";
        class Root extends PathTask {
            @Work
            public String exec() {
                System.out.println("ROOT-CALLED");
                got();
                return r1;
            }
        }
        class Consumer extends PathTask {
            String s = null;
            @Work
            public void exec(String s) {
                System.out.println("CONSUMER-CALLED " + s);
                got(s);
                this.s = s;
            }
        }
        Root root = new Root();
        PathTask taskRoot = track.work(root);
        Consumer consumer = new Consumer(); 
        PathTask taskConsumer = track.work(consumer).exp(r1);
        verify(0);
        assertEquals(r1,consumer.s);
    }
}

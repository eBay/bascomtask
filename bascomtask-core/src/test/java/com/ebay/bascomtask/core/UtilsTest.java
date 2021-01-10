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

import com.ebay.bascomtask.annotations.Light;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

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

    static class Base {
        @Light
        public int bar() {return 1;}
    }

    static class Sub extends Base {
        public int bar() {return super.bar();}
    }

    @Test
    public void annotationPresent() throws Exception {
        Method method = Sub.class.getMethod("bar");
        Sub sub = new Sub();
        assertEquals(1,sub.bar()); // just to keep static analysis tools happy
        Light ann = Utils.getAnnotation(sub,method,Light.class);
        assertNotNull(ann);
    }

    @Test
    public void annotationNoPresent() throws Exception {
        Method method = Sub.class.getMethod("bar");
        Sub sub = new Sub();
        assertEquals(1,sub.bar()); // just to keep static analysis tools happy
        Override ann = Utils.getAnnotation(sub,method,Override.class);
        assertNull(ann);
    }
}

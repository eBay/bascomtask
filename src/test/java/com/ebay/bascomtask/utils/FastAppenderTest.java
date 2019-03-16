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
package com.ebay.bascomtask.utils;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class FastAppenderTest {
    
    private static final String s1 = "one";
    private static final String s2 = "two";
    private static final String s3 = "three";

    @Test
    public void testSimpleUnder() {
        FastAppender<String> list = new FastAppender<>(new String[1]);
        list.add(s1);
        assertThat(1,equalTo(list.size()));
        assertThat(list.toList(),contains(s1));
    }

    @Test
    public void testSimpleOver() {
        FastAppender<String> list = new FastAppender<>(new String[1]);
        list.add(s1);
        list.add(s2);
        assertThat(2,equalTo(list.size()));
        assertThat(list.toList(),contains(s1,s2));
    }
    
    @Test
    public void testSimpleOverAndOne() {
        FastAppender<String> list = new FastAppender<>(new String[1]);
        list.add(s1);
        list.add(s2);
        list.add(s3);
        assertThat(3,equalTo(list.size()));
        assertThat(list.toList(),contains(s1,s2,s3));
    }
    
    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testGetLow() {
        FastAppender<String> list = new FastAppender<>(new String[1]);
        assertThat(0,equalTo(list.size()));
        list.get(-1);
    }
    
    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testGetHigh() {
        FastAppender<String> list = new FastAppender<>(new String[1]);
        list.get(1);
    }
    
    @Test
    public void loopTest() {
        String[] ss = new String[100];
        for (int i=0; i<ss.length; i++) {
            ss[i] = "x" + i;
        }

        final int loops = 100000;
        long ud = runJavaList(ss,loops,false);
        long sd = runJavaList(ss,loops,true);
        long ad = runAList(ss,loops);
        float udiff = ad / (float)ud;
        float sdiff = sd / (float)ad;
        System.out.printf("Alist is %.1fx slower than unsync\n",udiff);
        System.out.printf("Sync is %.1fx slower than alist\n",sdiff);
    }

    private long runJavaList(String[] ss, int loops, boolean sync) {
        List<String> javaList = Arrays.asList(ss);
        if (sync) {
            javaList = Collections.synchronizedList(javaList);
        }
        long start = System.nanoTime();
        while (loops-- > 0) {
            for (int i=0; i<ss.length; i++) {
                if (javaList.get(i) == "xxx") {
                    System.out.println("NULL");;
                }
            }
        }
        return report("Java-"+sync,start);
    }
    
    private long runAList(String[] ss, int loops) {
        FastAppender<String> list = new FastAppender<>(new String[ss.length]);
        for (int i=0; i<ss.length; i++) {
            list.add(ss[i]);
        }
        long start = System.nanoTime();
        while (loops-- > 0) {
            for (int i=0; i<ss.length; i++) {
                if (list.get(i) == "xxx") {
                    System.out.println("NULL");;
                }
            }
        }
        return report("FastAppender",start);
    }

    private long report(String msg, long start) {
        long duration = System.nanoTime() - start;
        System.out.printf("%s: %.2f\n",msg,duration/1000D);
        return duration;
    }
}

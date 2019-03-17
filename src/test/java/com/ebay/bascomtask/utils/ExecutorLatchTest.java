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


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class ExecutorLatchTest {
    
    private final ExecutorService e5 = Executors.newFixedThreadPool(5);
    
    private static void sleep(int duration) {
        try {
            Thread.sleep(duration);
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interrupt",e);
        }
    }
    
    @Test
    public void executeFrom() {
        ExecutorLatch latch = new ExecutorLatch(e5);
        final AtomicInteger i = new AtomicInteger(0);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                sleep(1);
                i.incrementAndGet();
            }
        };
        latch.executeFrom(r,true);
        latch.executeFrom(r,false);
        latch.workWait();
        assertThat(latch.getNumberOfThreadsCreated(),equalTo(2));
        assertThat(i.get(),equalTo(2));
    }

    @Test
    public void whenNoWork_exitImmediately() {
        ExecutorLatch latch = new ExecutorLatch(e5);
        latch.workWait();
        assertThat(latch.getNumberOfThreadsCreated(),equalTo(0));
    }

    @Test
    public void whenExecuteBeforeWorkWait_oneThreadCreated() {
        ExecutorLatch latch = new ExecutorLatch(e5);
        latch.execute(new Runnable() {
            @Override
            public void run() {
                sleep(1);
            }
        });
        latch.workWait();
        assertThat(latch.getNumberOfThreadsCreated(),equalTo(1));
    }

    @Test
    public void whenWaiting_noNewThreadCreated() {
        final ExecutorLatch latch = new ExecutorLatch(e5);
        final Thread[] top = new Thread[1];
        nest(latch,top,false);
        latch.workWait();
        assertThat(latch.getNumberOfThreadsCreated(),equalTo(1));
        assertThat(top[0],equalTo(Thread.currentThread()));
    }

    @Test
    public void whenWaiting_newThreadCreated() {
        final ExecutorLatch latch = new ExecutorLatch(e5);
        final Thread[] top = new Thread[1];
        nest(latch,top,true);
        latch.workWait();
        assertThat(latch.getNumberOfThreadsCreated(),equalTo(2));
        assertThat(top[0],equalTo(Thread.currentThread()));
    }
    
    private void nest(final ExecutorLatch latch, final Thread[] top, final boolean nested) {
        latch.execute(new Runnable() {
            @Override
            public void run() {
                sleep(10);
                latch.execute(new Runnable() {
                    @Override
                    public void run() {
                        top[0] = Thread.currentThread();
                        if (nested) {
                            latch.execute(new Runnable() {
                                @Override
                                public void run() {
                                    sleep(1);
                                }
                            });
                        }
                    }
                });
            }
        });
    }
    
    @Test
    public void whenWaiting_topLevelWorksTwice() {
        final ExecutorLatch latch = new ExecutorLatch(e5);
        final Thread[] top = new Thread[2];

        latch.execute(new Runnable() {
            @Override
            public void run() {
                sleep(10);
                latch.execute(new Runnable() {
                    @Override
                    public void run() {
                        top[0] = Thread.currentThread();
                    }
                });
                sleep(10); // Give enough time for previous thread to terminate
                latch.execute(new Runnable() {
                    @Override
                    public void run() {
                        top[1] = Thread.currentThread();
                    }
                });
            }
        });
        
        latch.workWait();
        assertThat(latch.getNumberOfThreadsCreated(),equalTo(1));
        assertThat(top[0],equalTo(Thread.currentThread()));
        assertThat(top[1],equalTo(Thread.currentThread()));
    }
}


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

import java.util.List;

import javax.management.RuntimeErrorException;

import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.ebay.bascomtask.annotations.PassThru;
import com.ebay.bascomtask.annotations.Rollback;
import com.ebay.bascomtask.annotations.Scope;
import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;
import com.ebay.bascomtask.exceptions.RuntimeGraphError;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for rolling back execution
 * 
 * @author brendanmccarthy
 */
@SuppressWarnings("unused")
@SuppressFBWarnings("UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS")
public class RollbackTest extends PathTaskTestBase {
    
    static class ForceException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        ForceException() {
            super("forced");
        }
    }
    
    abstract class Base {
        long workAt;
        long rollBackAt;
        void work() {
            workAt = System.nanoTime();
        }
        void back() {
            rollBackAt = System.nanoTime();
        }
        void rolledBack(boolean expRollBack) {
            if (expRollBack) {
                assertThat(rollBackAt,is(greaterThan(0L)));
            }
            else {
                assertThat(rollBackAt,is(equalTo(0L)));
            }
        }
        void executed(boolean expRollBack) {
            assertThat(workAt,is(greaterThan(0L)));
            rolledBack(expRollBack);
        }
        void ordered(Base b, boolean expRollBack) {
            executed(expRollBack);
            b.executed(expRollBack);
            assertThat(workAt,is(lessThan(b.workAt)));
            if (expRollBack) {
                assertThat(rollBackAt,is(greaterThan(b.rollBackAt)));
            }
        }
    }
    
    abstract class Direct extends Base {
        @Work public void work() {
            super.work();
        }
        @Rollback public void back() {
            super.back();
        }
    }
    
    @Test
    public void testOne() {
        class A extends Direct {}
        
        A a = new A();
        Orchestrator orc = Orchestrator.create();
        orc.addWork(a);
        orc.execute();
        orc.rollback();
        assertThat(a.workAt,is(greaterThan(0L)));
        assertThat(a.rollBackAt,is(greaterThan(0L)));
    }
    
    
    @Test(expected=ForceException.class)
    public void testOnePlusEx() {
        class A extends Direct {}
        class B extends Base {
            @Work public void work(A a) {
                throw new ForceException();
            }
        }
        
        A a = new A();
        B b = new B();
        Orchestrator orc = Orchestrator.create();
        orc.addWork(a);
        orc.addWork(b);
        try {
            orc.execute();
        }
        finally {
            a.executed(true);
        }
    }
    
    @Test
    public void testTwo() {
        class A extends Direct {}
        class B extends Base {
            @Work public void work(A a) {
                super.work();
            }
            @Rollback public void back() {
                super.back();
            }
        }
        
        A a = new A();
        B b = new B();
        Orchestrator orc = Orchestrator.create();
        orc.addWork(a);
        orc.addWork(b);
        orc.execute();
        orc.rollback();
        a.ordered(b,true);
    }
    
    
    @Test(expected=ForceException.class)
    public void testTwoPlusEx() {
        class A extends Direct {}
        class B extends Base {
            @Work public void work(A a) {
                super.work();
            }
            @Rollback public void back() {
                super.back();
            }
        }
        class C extends Base {
            @Work public void work(B b) {
                throw new ForceException();
            }
        }        
        
        A a = new A();
        B b = new B();
        C c = new C();
        Orchestrator orc = Orchestrator.create();
        orc.addWork(a);
        orc.addWork(b);
        orc.addWork(c);
        try {
            orc.execute();
        }
        finally {
            a.ordered(b,true);
            c.rolledBack(false);
        }
    }

    @Test(expected=ForceException.class)
    public void threeTwoPlusEx() {
        class A extends Direct {}
        class B extends Base {
            @Work public void work(A a) {
                super.work();
            }
            @Rollback public void back() {
                super.back();
            }
        }
        class C extends Base {
            @Work public void work(B b) {
                super.work();
            }
            @Rollback public void back() {
                super.back();
            }
        }
        class D extends Base {
            @Work public void work(C c) {
                throw new ForceException();
            }
        }        
        
        A a = new A();
        B b = new B();
        C c = new C();
        D d = new D();
        Orchestrator orc = Orchestrator.create();
        orc.addWork(a);
        orc.addWork(b);
        orc.addWork(c);
        orc.addWork(d);
        try {
            orc.execute();
        }
        finally {
            a.ordered(b,true);
            b.ordered(c,true);
            d.rolledBack(false);
        }
    }
}


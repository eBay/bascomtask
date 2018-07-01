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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.config.BascomConfigFactory;
import com.ebay.bascomtask.config.DefaultBascomConfig;
import com.ebay.bascomtask.config.IBascomConfig;
import com.ebay.bascomtask.config.ITaskInterceptor;

/**
 * Tests related to BascomTask configuration.
 * @author brendanmccarthy
 */
public class ConfigTest {

	private IBascomConfig config;
	
	@Before
	public void before() {
	    config = BascomConfigFactory.getConfig();
	}
	
	@After
	public void after() {
	    // Be sure to reset to the way it was prior to test, so that later tests won't get affected
	    BascomConfigFactory.setConfig(config);
	}
    
    static class A {
        boolean hit = false;
        @Work public void exec() {hit = true;}
    }
    static class B {
        boolean hit = false;
        @Work public void exec(A a) {hit = true;}
    }
    
	@Test
	public void testOnlyDefaultInterceptor() {
	    
		final A a = new A();
		B b = new B();
		Orchestrator orc = Orchestrator.create();
		orc.addWork(a);
		orc.addWork(b);
		orc.execute();
		
		assertTrue(a.hit);
		assertTrue(b.hit);
	}

	private ITaskInterceptor createInterceptorAvoiding(final Object avoidExecutingThis) {
	    return new ITaskInterceptor() {
	        @Override
	        public boolean invokeTaskMethod(ITaskMethodClosure closure) {
	            Object target = closure.getTargetPojoTask();
	            if (target==avoidExecutingThis) {
	                return true; // Do not execute, but make available as parameter
	            }
	            return closure.executeTaskMethod();
	        } 
	    };
	}
	
	@Test
	public void testInterceptorSetOnOrchestrator() {
	    
		final A a = new A();
		B b = new B();
		Orchestrator orc = Orchestrator.create().interceptor(createInterceptorAvoiding(a));
		orc.addWork(a);
		orc.addWork(b);
		orc.execute();
		
		assertFalse(a.hit);
		assertTrue(b.hit);
	}
	
	@Test
	public void testInterceptorSetByDefault() {
		final A a = new A();
		B b = new B();
	    
		BascomConfigFactory.setConfig(new DefaultBascomConfig() {
		    @Override
		    public ITaskInterceptor getDefaultInterceptor() {
		        return createInterceptorAvoiding(a);
		    }});

		Orchestrator orc = Orchestrator.create();
		orc.addWork(a);
		orc.addWork(b);
		orc.execute();

		assertFalse(a.hit);
		assertTrue(b.hit);
	}
	
	@Test
	public void testClosureMethodsValid() {
	    class Holder {
	        ITaskMethodClosure closure;
	    }
	    final Holder holder = new Holder();
	    
	    final long BAR_DURATION = 10;
	    final String FOO_NAME = "Purple";
		class Foo {@Override public String toString() {return FOO_NAME;}}
	    
		class Bar {
		    @Work public void gobar(Foo foo) {
		        try {
                    Thread.sleep(BAR_DURATION);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                };
		    }
		}
	    
		Orchestrator orc = Orchestrator.create().interceptor(
		        new ITaskInterceptor() {
		            @Override
		            public boolean invokeTaskMethod(ITaskMethodClosure closure) {
		                holder.closure = closure;
		                closure.executeTaskMethod();
		                return true;
		            } 
		        }).name("ORK");

		Foo foo = new Foo();
		Bar bar = new Bar();
		orc.addWork(foo);
		final String BAR_NAME = "Green";
		orc.addWork(bar).name(BAR_NAME);
		orc.execute();
		ITaskMethodClosure closure = holder.closure;
		assertNotNull(closure);
		assertTrue(closure.getMethodName().equals("gobar"));
		assertThat(closure.getMethodActualSignature(),containsString(FOO_NAME));
		assertSame(foo,closure.getMethodBindings()[0]);
		assertThat(closure.getMethodFormalSignature(),containsString(Foo.class.getSimpleName()));
		assertSame(bar,closure.getTargetPojoTask());
		assertEquals(BAR_NAME,closure.getTaskName());
		assertThat(closure.getDurationMs(),is(greaterThanOrEqualTo(BAR_DURATION)));
		assertThat(closure.getDurationNs(),is(greaterThan(closure.getDurationMs())));
	}
}



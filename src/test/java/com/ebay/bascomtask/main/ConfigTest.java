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
}



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

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.config.BascomConfigFactory;
import com.ebay.bascomtask.config.DefaultBascomConfig;
import com.ebay.bascomtask.config.IBascomConfig;
import com.ebay.bascomtask.config.ITaskClosureGenerator;

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

	private ITaskClosureGenerator createInterceptorAvoiding(final Object avoidExecutingThis) {
	    return new ITaskClosureGenerator() {
            @Override
            public TaskMethodClosure getClosure() {
                return new TaskMethodClosure() {
                    @Override
                    public boolean executeTaskMethod() {
                        Object target = getTargetPojoTask();
                        if (target==avoidExecutingThis) {
                            return true; // Do not execute, but make available as parameter
                        }
                        return super.executeTaskMethod();
                    }
                };
            }
        };
	}
	
	@Test
	public void testInterceptorSetOnOrchestrator() {
	    
		final A a = new A();
		B b = new B();
		Orchestrator orc = Orchestrator.create().closureGenerator(createInterceptorAvoiding(a));
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
		    public ITaskClosureGenerator getExecutionHook(Orchestrator orc, String pass) {
		        return new ITaskClosureGenerator() {
		            @Override
		            public TaskMethodClosure getClosure() {
		                return createInterceptorAvoiding(a).getClosure();
		            }
		        };
		    }}
		);

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
	        TaskMethodClosure closure;
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
	    
		Orchestrator orc = Orchestrator.create().closureGenerator(
		        new ITaskClosureGenerator() {
                    @Override
                    public TaskMethodClosure getClosure() {
		                holder.closure = new TaskMethodClosure();
		                return holder.closure;
                    }
		        }).name("ORK");

		Foo foo = new Foo();
		Bar bar = new Bar();
		orc.addWork(foo);
		final String BAR_NAME = "Green";
		orc.addWork(bar).name(BAR_NAME);
		orc.execute();
		TaskMethodClosure closure = holder.closure;
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

	@Test
	public void testClosurePreparesAndExecsInDifferentThreads() {
	    class X {
	        Thread prepThread = null;
	        Thread execThread = null;
	        @Work public void exec() {}
	    }
	    
		Orchestrator orc = Orchestrator.create().closureGenerator(
		        new ITaskClosureGenerator() {
                    @Override
                    public TaskMethodClosure getClosure() {
		                return new TaskMethodClosure() {
		                    public void prepareTaskMethod() {
		                        Object pojo = getTargetPojoTask();
		                        if (pojo instanceof X) {
		                            X x = (X)pojo;
		                            x.prepThread = Thread.currentThread();
		                        }
		                    }

		                    public boolean executeTaskMethod() {
		                        Object pojo = getTargetPojoTask();
		                        if (pojo instanceof X) {		                        
		                            X x = (X)pojo;
		                            x.execThread = Thread.currentThread();
		                        }
		                        return true;
		                    }		                    
		                };
                    }
		        }).name("ORK");

	    final X x1 = new X();
	    final X x2 = new X();
	    
		orc.addWork(x1);
		orc.addWork(x2).fork();
		orc.execute();
		
		assertNotNull(x1.prepThread);
		assertSame(x1.prepThread,x1.execThread);
		assertNotNull(x2.prepThread);
		assertNotSame(x2.prepThread,x2.execThread);
	}
	
	@Test
	public void testPreparesMatchExecs() {
	    final List<Object> prepares = new ArrayList<>();
	    final List<Object> execs = new ArrayList<>();
	    class X {
	    }
	    class Y {
	        @Work public void exec(X x) {}
	    }
	    class Z {
	        @Work public void exec(Y y) {}
	    }
	    
		Orchestrator orc = Orchestrator.create().closureGenerator(
		        new ITaskClosureGenerator() {
                    @Override
                    public TaskMethodClosure getClosure() {
		                return new TaskMethodClosure() {
		                    public void prepareTaskMethod() {
		                        Object pojo = getTargetPojoTask();
		                        if (pojo.getClass() == X.class) {
		                            System.out.println("HIT");
		                        }
		                        prepares.add(pojo);
		                    }

		                    public boolean executeTaskMethod() {
		                        Object pojo = getTargetPojoTask();
		                        execs.add(pojo);
		                        return true;
		                    }		                    
		                };
                    }
		        });

	    final X x = new X();  // Shouldn't be either list, since it has no exec
	    final Y y1 = new Y(); // Should be in both lists
	    final Y y2 = new Y(); // Should be in both lists
	    final Z z = new Z();  // Should be in both lists twice
	    
		orc.addWork(x);
		orc.addWork(y1);
		orc.addWork(y2);
		orc.addWork(z);
		orc.execute();
		
		assertThat(prepares,containsInAnyOrder(y1,y2,z,z));
		assertThat(execs,containsInAnyOrder(y1,y2,z,z));
	}
	
	class BaseTask {
	    String pass = null;
	    MyTaskClosure parent = null;
	}

	class MyTaskClosure extends TaskMethodClosure {
	    private final String pass;
	    private final MyTaskClosure parent;
	    MyTaskClosure(String pass, MyTaskClosure parent) {
	        this.pass = pass;
	        this.parent = parent;
	    }

	    @Override
	    public boolean executeTaskMethod() {
	        Object pojo = getTargetPojoTask();
	        if (pojo instanceof BaseTask) {
	            BaseTask task = (BaseTask)pojo;
	            task.pass = pass;
	            task.parent = parent;
	        }
	        else {
	            fail("Not a BaseTask:  + pojo");
	        }
	        return true;
	    }
	    @Override
	    public TaskMethodClosure getClosure() {
	        return new MyTaskClosure(pass,this);
	    }
	}

	@Test
	public void testClosureParenting() {
	    
	    final String PASS = "foobar";
	    
	    class X extends BaseTask {
	        @Work public void exec() {}
	    }
	    
	    class Y extends BaseTask {
	        @Work public void exec(X x) {}
	    }
	    
		BascomConfigFactory.setConfig(new DefaultBascomConfig() {
		    @Override
		    public ITaskClosureGenerator getExecutionHook(Orchestrator orc, final String pass) {
		        return new ITaskClosureGenerator() {
		            @Override
		            public TaskMethodClosure getClosure() {
		                return new MyTaskClosure(pass,null);
		            }
		        };
		    }}
		);

		BascomConfigFactory.setConfig(new DefaultBascomConfig() {
		    @Override
		    public ITaskClosureGenerator getExecutionHook(Orchestrator orc, final String pass) {
		        return new ITaskClosureGenerator() {
		            @Override
		            public TaskMethodClosure getClosure() {
		                return new TaskMethodClosure() {
		                    @Override
		                    public boolean executeTaskMethod() {
		                        System.out.println("Start " + getMethodFormalSignature());
		                        try {
		                            return super.executeTaskMethod();
		                        }
		                        finally {
		                            System.out.println("End " + getMethodFormalSignature());
		                        }		                        
		                    }
		                };
		            }
		        };
		    }}
		);

		
	    X x = new X();
	    Y y = new Y();
	    Orchestrator orc = Orchestrator.create();
	    orc.addWork(x);
	    orc.addWork(y);
	    orc.execute(PASS);
	    
	    assertEquals(PASS,x.pass);
	    assertEquals(PASS,y.pass);
	    assertNull(x.parent);
	    assertNotNull(y.parent);
	}
}



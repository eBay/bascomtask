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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.ebay.bascomtask.annotations.PassThru;
import com.ebay.bascomtask.annotations.Scope;
import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for normal and exceptional behaviors on many graph variants.
 * @author brendanmccarthy
 */
@SuppressWarnings("unused")
@SuppressFBWarnings("UMAC_UNCALLABLE_METHOD_OF_ANONYMOUS_CLASS") // Simplistic rule misses valid usage
public class OrchestrationTest extends PathTaskTestBase {
	
	@Test
	public void testEmpty() {
		verify(0);
	}
	
	@Test
	public void testNoMethods() {
		class A extends PathTask {
		}
		PathTask a = track.work(new A());
		verify(0);
	}

	@Test(expected=InvalidTask.NameConflict.class)
	public void testNameConflict() {
		class A extends PathTask {}
		track.work(new A()).name("foo");
		track.work(new A()).name("foo");
	}

	@Test
	public void test1SimpleActive() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		A a = new A();
		PathTask taskA = track.work(a);
		verify(0);
	}
	
	@Test
	public void test1SimplePassive() {
		class A extends PathTask {
			@PassThru public void exec() {got();}
		}
		A a = new A();
		PathTask taskA = track.passThru(a);
		verify(0);
	}
	
	@Test(expected=InvalidTask.NotPublic.class)
	public void testNonPublicMethod() {
		class A extends PathTask {
			@Work void exec() {got();}
		}
		A a = new A();
		PathTask taskA = track.work(a);
		verify(0);
	}

	@Test(expected=InvalidTask.BadReturn.class)
	public void testBadReturnType() {
		class A extends PathTask {
			@Work public int exec() {got();return 0;}
		}
		A a = new A();
		PathTask taskA = track.work(a);
		verify(0);
	}

	@Test(expected=InvalidTask.BadParam.class)
	public void testNonTaskParam() {
		class A extends PathTask {
			@Work public void exec(int x) {got();}
		}
		A a = new A();
		PathTask taskA = track.work(a);
		verify(0);
	}
	
	@Test(expected=InvalidGraph.MissingDependents.class)
	public void testMissingDependencies() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec(A a) {got(a);}
		}
		B b = new B();
		PathTask taskB = track.work(b);
		verify(0);
	}
	
	@Test
	public void testJustOneMethodMissingDependenciesOk() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec() {got();}
		}
		class C extends PathTask {
			@Work public void exec(A a) {got(a);}
			@Work public void exec(B b) {got(b);}
		}
		B b = new B();
		C c = new C();
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c).exp(b);
		verify(0);
	}
	
	@Test
	public void test2Linear() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec(A a) {got(a);}
		}
		A a = new A();
		B b = new B();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b).exp(a);
		verify(0);
	}
	
	@Test
	public void test3Linear() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec(A a) {got(a);}
		}
		class C extends PathTask {
			@Work public void exec(B b) {got(b);}
		}

		A a = new A();
		B b = new B();
		C c = new C();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b).exp(a);
		PathTask taskC = track.work(c).exp(b);
		verify(0);
	}
	
	@Test
	public void test2Indepdendent() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec() {got();}
		}
		A a = new A();
		B b = new B();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		verify(1);
	}

	@Test
	public void test2Dependents() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec() {got();}
		}
		class C extends PathTask {
			@Work public void exec(A a, B b) {
				got(a,b);
			}
		}
		
		A a = new A();
		B b = new B();
		C c = new C();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c).exp(a,b);
		verify(1);
	}
	
	@Test(expected=RuntimeException.class)
	public void test1ParException() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec() {throw new RuntimeException("no good!");}
		}
		A a = new A();
		B b = new B();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		verify(1);
	}
	

	@Test
	public void test3LinearDup() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec(A a) {got(a);}
		}
		class C extends PathTask {
			@Work public void exec(A a, B b) {got(a,b);}
		}

		A a = new A();
		B b = new B();
		C c = new C();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b).exp(a);
		PathTask taskC = track.work(c).exp(a,b);
		verify(0);
	}
	
	@Test
	public void testIgnoreTaskMethods() {
		class A extends PathTask {
			boolean hit = false;
			@Work public void exec() {hit=true;}
		}
		A a = new A();
		PathTask taskA = track.ignoreTaskMethods(a);
		verify(0);
		assertFalse(a.hit);
	}
	
	@Test
	public void testIgnoreTaskMethodsWithDependency() {
		class A extends PathTask {
			boolean hit = false;
			@Work public void exec() {hit=true;}
		}
		class B extends PathTask {
			@Work public void exec(A a) {got(a);}
		}
		A a = new A();
		B b = new B();
		PathTask taskA = track.ignoreTaskMethods(a);
		PathTask taskB = track.work(b).exp(a);
		verify(0);
		assertFalse(a.hit);
	}
	
	@Test
	public void test2Dependents1Light() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work(light=true) public void exec() {got();}
		}
		class C extends PathTask {
			@Work public void exec(A a, B b) {got(a,b);}
		}

		A a = new A();
		B b = new B();
		C c = new C();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c).exp(a,b);
		verify(0);
	}

	class Top extends PathTask {
		@Work public void exec() {got();}
	}
	class Left extends PathTask {
		@Work public void exec(Top top) {got(top);}
	}
	class Right extends PathTask {
		@Work public void exec(Top top) {got(top);}
	}
	class Bottom extends PathTask {
		@Work public void exec(Left left, Right right) {got(left,right);}
	}
	
	private void testDiamondWithDelays(int td, int ld, int rd, int bd) {
		PathTask top = track.work(new Top()).sleepFor(td);
		PathTask left = track.work(new Left()).sleepFor(ld).exp(top);
		PathTask right = track.work(new Right()).sleepFor(rd).exp(top);
		PathTask bottom = track.work(new Bottom()).sleepFor(bd).exp(left,right);
		verify(1);
	}
	
	@Test
	public void testDiamond0000() {
		testDiamondWithDelays(0,0,0,0);
	}
	
	@Test
	public void testDiamond0x00() {
		testDiamondWithDelays(0,50,0,0);
	}
	
	@Test
	public void testDiamond00x0() {
		testDiamondWithDelays(0,0,50,0);
	}
	
	@Test(expected=InvalidGraph.Circular.class)
	public void testSelfRefFail() {
		class A extends PathTask {
			@Work public void exec(A a) {}
		}
		A a = new A();
		PathTask taskA = track.work(a);
		verify(0);
	}

	// These defined outside of testCircular3Fail because forward ref 
	// would otherwise cause compiler to complain
	class CA extends PathTask {
		@Work public void exec(CC c) {}
	}
	class CB extends PathTask {
		@Work public void exec(CA a) {}
	}
	class CC extends PathTask {
		@Work public void exec(CB b) {}
	}
	
	@Test(expected=InvalidGraph.Circular.class)
	public void testCircular3Fail() {
		CA a = new CA();
		CB b = new CB();
		CC c = new CC();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c);
		verify(0);
	}
	
	@Test
	public void testPassThruNotCalledWhenNotPassive() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@PassThru public void exec(A a) {throw new RuntimeException("Didn't expect to be called");}
			@Work public void exec() {got();}
		}

		A a = new A();
		B b = new B();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b); // Not passive
		verify(1);
	}
	
	@Test
	public void test3LinearOnePassThru() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@PassThru public void exec(A a) {got(a);}
			@Work public void exec() {got();}   // Shouldn't get called
		}
		class C extends PathTask {
			@Work public void exec(B b) {got(b);}
		}

		A a = new A();
		B b = new B();
		C c = new C();
		PathTask taskA = track.work(a);
		PathTask taskB = track.passThru(b).exp(a);
		PathTask taskC = track.work(c).exp(b);
		verify(0);
	}
	
	@Test
	public void testDependentHasNoExecutableMethod() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec(A a) {got(a);}
		}
		
		A a = new A();
		B b = new B();
		PathTask taskA = track.passThru(a);  // No @PassThru on A, but it should be exposed to B
		PathTask taskB = track.work(b).exp(a);
		verify(0);
	}
	
	@Test
	public void testParexStats() {
	    final int SLEEP_MAX = 100;
		class A extends PathTask {
			@Work public void exec() {got();sleep(50);}
		}
		class B extends PathTask {
			@Work public void exec() {got();sleep(50);}
		}
		class C extends PathTask {
			@Work public void exec(A a, B b) {got(a,b);sleep(SLEEP_MAX);} // nowait below
		}
		class D extends PathTask {
			@Work public void exec(A a, B b) {got(a,b);sleep(50);}
		}

		A a = new A();
		B b = new B();
		C c = new C();
		D d = new D();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c).exp(a,b).noWait().fork();
		PathTask taskD = track.work(d).exp(a,b);
		verify(1,2,false);
		Orchestrator.ExecutionStats stats = track.orc.getStats();
		Orchestrator.ExecutionStats noWaitStats = track.orc.getNoWaitStats();
		assertEquals(3,stats.getNumberOfTasksExecuted());
		assertEquals(stats,noWaitStats);
		long sav = stats.getParallelizationSaving();
		assertTrue("got-stats-before " + sav,sav > 20 && sav < 80);
		sleep(SLEEP_MAX);
		stats = track.orc.getStats();
		noWaitStats = track.orc.getNoWaitStats();
		assertEquals(3,stats.getNumberOfTasksExecuted());
		assertEquals(4,noWaitStats.getNumberOfTasksExecuted());
		assertTrue("got-stats-after " + sav,sav > 20 && sav < 80);
		long nws = noWaitStats.getParallelizationSaving();
		assertTrue(noWaitStats.getParallelizationSaving() > stats.getParallelizationSaving());
		assertTrue(noWaitStats.getExecutionTime() > stats.getExecutionTime());
	}
	
	@Test public void testStatsEqualityAgainstAddMethods() {
	    Orchestrator orc = Orchestrator.create();
	    assertNotNull(orc.toString());
	    Orchestrator.ExecutionStats stats1 = orc.getStats();
	    assertEquals(stats1,stats1);
	    Orchestrator.ExecutionStats stats2 = orc.getStats();
	    orc.addWork(new Object() {@Work public void exec() {}});
	    orc.execute();
	    Orchestrator.ExecutionStats stats3 = orc.getStats();
	    assertNotEquals(stats1,stats3);
	    orc.addPassThru(new Object() {@Work public void exec() {}});
	    orc.execute();
	    Orchestrator.ExecutionStats stats4 = orc.getStats();
	    assertNotEquals(stats1,stats4);
	    orc.addIgnoreTaskMethods(new Object() {@Work public void exec() {}});
	    orc.execute();
	    Orchestrator.ExecutionStats stats5 = orc.getStats();
	    assertNotEquals(stats1,stats5);
	    System.out.println("Final stats="+stats5);
	}
	
	@Test
	public void testComplex() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@PassThru public void exec(A a) {got(a);}
		}
		class C extends PathTask {
			@Work public void exec(B b) {got(b);}
		}
		class D extends PathTask {
			@Work public void exec() {got();}
		}
		class E extends PathTask {
			@Work public void exec(C c, D d) {got(c,d);}
		}
		class F extends PathTask {
			@Work public void exec(D d) {got(d);}
		}
		class G extends PathTask {
			@Work public void exec(C c, F f) {got(c,f);}
		}
		class H extends PathTask {
			@Work public void exec(A a, G g) {got(a,g);}
		}
		
		A a = new A();
		B b = new B();
		C c = new C();
		D d = new D();
		E e = new E();
		F f = new F();
		G g = new G();
		H h = new H();
		PathTask taskA = track.work(a);
		PathTask taskB = track.passThru(b).exp(a);
		PathTask taskC = track.work(c).exp(b);
		PathTask taskD = track.work(d);
		PathTask taskE = track.work(e).exp(c,d);
		PathTask taskF = track.work(f).exp(d);
		PathTask taskG = track.work(g).exp(c,f);
		PathTask taskH = track.work(h).exp(a,g);
		verify(1,2);
	}
	
	private void testPathExecutedConditionally(final boolean bReturnsTrue) {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public boolean exec(A a) {got(a);return bReturnsTrue;}
		}
		class C extends PathTask {
			@Work public void exec(A a) {got(a);}
		}
		class D extends PathTask {
			@Work public void exec(B b) {got(b);}
			@Work public void exec(C c) {got(c);}
		}
		
		A a = new A();
		B b = new B();
		C c = new C();
		D d = new D();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b).exp(a);
		PathTask taskC = track.work(c).exp(a);
		PathTask taskD = track.work(d).exp(c).multiMethodOk();
		if (bReturnsTrue) {
			taskD = taskD.exp(b);
		}
		verify(1);
	}

	@Test
	public void testOnlyOnePathExecuted() {
		testPathExecutedConditionally(false);
	}

	@Test
	public void testBothPathsExecuted() {
		testPathExecutedConditionally(true);
	}
	
	private void testReturn(final boolean which) {
		class A extends PathTask {
			@Work public boolean exec() {got();return which;}
		}
		A a = new A();
		PathTask taskA = track.work(a);
		verify(0);
	}

	@Test
	public void testReturnTrue() {
		testReturn(true);
	}
	
	@Test
	public void testReturnFalse() {
		testReturn(false);
	}
	
	private void testReturnTwoDeep(final boolean which) {
		class A extends PathTask {
			@Work public boolean exec() {got();return which;}
		}
		class B extends PathTask {
			boolean exec = false;
			@Work public void exec(A a) {got(a); exec = true;}
		}
		A a = new A();
		B b = new B();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		if (which) {
			taskB = taskB.exp(a);
		}
		verify(0);
		assertEquals(which,b.exec); // Graph completes in either case, but if A.exec returns false then B.exec should not execute
	}

	@Test
	public void testReturnTwoDeepTrue() {
		testReturnTwoDeep(true);
	}
	
	@Test
	public void testReturnTwoDeepFalse() {
		testReturnTwoDeep(false);
	}

	//@Test 
	public void testReturnMixedIncoming() {
		class A extends PathTask {
			final boolean which;
			A(boolean which) {
				this.which = which;
			}
			@Work public boolean exec() {got();return which;}
		}
		class B extends PathTask {
			AtomicInteger count = new AtomicInteger(0);
			@Work public void exec(A a) {got(a); count.incrementAndGet();}
		}
		A a1 = new A(true);
		A a2 = new A(false);
		B b = new B();
		PathTask taskA1 = track.work(a1);
		PathTask taskA2 = track.work(a2);
		PathTask taskB = track.work(b);
		verify(0);
		assertEquals(1,b.count.get()); // Graph completes , but B.exec should exec only a1
	}


	private void multiMethodResponse(boolean allow) {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec() {got();}
		}
		class C extends PathTask {
			@Work public void exec(A a) {got(a);}
			@Work public void exec(B b) {got(b);}
		}
		
		A a = new A();
		B b = new B();
		C c = new C();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c).exp(a).exp(b);
		if (allow) {
			taskC.multiMethodOk();
		}
		verify(1);
	}

	@Test
	public void testMultiMethodAllowed() {
		multiMethodResponse(true);
	}

	@Test(expected=InvalidGraph.MultiMethod.class)
	public void testMultiMethodRejected() {
		multiMethodResponse(false);
	}

	
	

	class ParTask extends PathTask {
		int nesting = 0;
		int maxNesting = 0;
		void glob() {
			if (++nesting > maxNesting) maxNesting = nesting;
			sleep(100);
			nesting--;
		}
	}
	
	@Test
	public void testMulti2() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends ParTask {
			@Work public void exec(A a) {
				got(a);
				glob();
			}
		}
		
		A a1 = new A();
		A a2 = new A();
		B b = new B();
		PathTask taskA1 = track.work(a1);
		PathTask taskA2 = track.work(a2);
		PathTask taskB = track.work(b).exp(a1).exp(a2);
		// Because ParTask sleeps, both A threads should have fired and executed at same time
		verify(1);
		assertEquals(2,b.maxNesting);
	}

	@Test
	public void testMulti2Sequence() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends ParTask {
			@Work(scope=Scope.SEQUENTIAL) public void exec(A a) {got(a);glob();}
		}
		A a1 = new A();
		A a2 = new A();
		B b = new B();
		PathTask taskA1 = track.work(a1).name("a1");
		PathTask taskA2 = track.work(a2).name("a2");
		PathTask taskB = track.work(b).exp(a1).exp(a2);
		
		verify(1);
		assertEquals(1,b.maxNesting);
	}

	@Test
	public void testMulti3() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec(A a) {got(a);}
		}
		class C extends PathTask {
			@Work public void exec(B b) {got(b);}
		}

		A a1 = new A();
		A a2 = new A();
		B b1 = new B();
		B b2 = new B();
		C c = new C();
		PathTask taskA1 = track.work(a1).name("a1");
		PathTask taskA2 = track.work(a2).name("a2");
		PathTask taskB1 = track.work(b1).name("b1").exp(a1).exp(a2);
		PathTask taskB2 = track.work(b2).name("b2").exp(a1).exp(a2);
		PathTask taskC = track.work(c).exp(b1).exp(b1).exp(b2).exp(b2);
		verify(2,3);
	}

	@Test
	public void testMulti2With2Params() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec() {got();}
		}
		class C extends PathTask {
			@Work public void exec(A a, B b) {got(a,b);}
		}

		A a1 = new A();
		A a2 = new A();
		B b1 = new B();
		B b2 = new B();
		C c = new C();
		PathTask taskA1 = track.work(a1);
		PathTask taskA2 = track.work(a2);
		PathTask taskB1 = track.work(b1).name("b1");
		PathTask taskB2 = track.work(b2).name("b2");
		PathTask taskC = track.work(c).exp(a1,b1).exp(a1,b2).exp(a2,b1).exp(a2,b2);
		verify(3,5);
	}
	
	/**
	 * Forces, in order to test the flow path, the 'push' of a task to the waiting main thread 
	 * -- unfortunately relies on internal knowledge about which task a thread (main in this case) 
	 * will keep for itself to execute. This works but if that order is innocuously changed in 
	 * the future then this test may break.
	 */
	@Test
	public void testMainThreadFollow() {
		class A extends PathTask {
			@Work public void exec() {got();sleep(100);}
		}
		class B extends PathTask {
			@Work public void exec() {got();}
		}
		class C extends PathTask {
			@Work public void exec(A a, B b) {got(a,b);}
		}
		class D extends PathTask {
			@Work public void exec(C c) {got(c);}
		}
		class E extends PathTask {
			@Work public void exec(C c) {got(c);}
		}

		A a = new A();
		B b = new B();
		C c = new C();
		D d = new D();
		E e = new E();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c).exp(a,b);
		PathTask taskD = track.work(d).exp(c);
		PathTask taskE = track.work(e).exp(c);
		verify(1);
	}
	
	@Test
	public void testFireNoWait() {
		final int DELAY = 100;
		class A extends PathTask {
			boolean done = false;
			@Work public void exec() {sleep(DELAY);done=true;}
		}
		A a = new A();
		PathTask taskA = track.work(a).noWait();  // Shouldn't be done until after DELAY
		verify(1,false);
		assertFalse(a.done);
		sleep(DELAY+10);
		assertTrue(a.done);
	}

	@Test
	public void testMixedFireNoWait() {
		final int DELAY = 100;
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			boolean done = false;
			@Work public void exec() {got();sleep(DELAY);done=true;}
		}
		class C extends PathTask {
			@Work public void exec(A a, B b) {got(a,b);}
		}
		A a = new A();
		B b = new B();
		C c = new C();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b).noWait();
		PathTask taskC = track.work(c).exp(a,b);
		
		// Orchestrator on its own wouldn't wait for B, but since it waits for C
		// it must by implication wait for B
		verify(1);
	}
	
	@Test
	public void testSimpleList() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec(List<A> as) {
				got(list(as));
			}
		}
		
		A a1 = new A();
		A a2 = new A();
		B b = new B();
		PathTask taskA1 = track.work(a1);
		PathTask taskA2 = track.work(a2);
		PathTask taskB = track.work(b).exp(list(a1,a2));
		verify(1);
	}

	@Test
	public void testListPlusNonListArg() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec() {got();}
		}
		class C extends PathTask {
			@Work public void exec(List<A> as, B b) {
				got(list(as),b);
			}
		}
		
		A a1 = new A();
		A a2 = new A();
		B b = new B();
		C c = new C();
		PathTask taskA1 = track.work(a1);
		PathTask taskA2 = track.work(a2);
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c).exp(list(a1,a2),b);
		verify(2,3);
	}

	@Test
	public void testListArgReceivedOnceDuringMultipleCalls() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		class B extends PathTask {
			@Work public void exec() {got();}
		}
		class C extends PathTask {
			@Work public void exec(List<A> as, B b) {
				got(list(as),b);
			}
		}
		
		A a1 = new A();
		A a2 = new A();
		B b1 = new B();
		B b2 = new B();
		C c = new C();
		PathTask taskA1 = track.work(a1);
		PathTask taskA2 = track.work(a2);
		PathTask taskB1 = track.work(b1);
		PathTask taskB2 = track.work(b2);
		PathTask taskC = track.work(c).exp(list(a1,a2),b1).exp(list(a1,a2),b2);
		verify(2,3);
	}
}



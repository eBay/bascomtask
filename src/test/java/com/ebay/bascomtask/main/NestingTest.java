package com.ebay.bascomtask.main;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.ebay.bascomtask.annotations.PassThru;
import com.ebay.bascomtask.annotations.Scope;
import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.exceptions.InvalidGraph;
import com.ebay.bascomtask.exceptions.InvalidTask;
import com.ebay.bascomtask.main.Orchestrator;
import com.ebay.bascomtask.main.Task;

/**
 * Tests for inline graph mods.
 * @author brendanmccarthy
 */
@SuppressWarnings("unused")
public class NestingTest extends PathTaskTestBase {
	
	@Test
	public void testSimpleNested() {
		class A extends PathTask {
			@Work public void exec() {
				class B extends PathTask {
					@Work public void exec() {got();}
				}
				B b = new B();
				PathTask taskB = track.work(b);
			}
		}
		A a = new A();
		PathTask taskA = track.work(a);
		verify(0);
	}

	@Test
	public void testNestedWithOuterDependency() {
		class A extends PathTask {
			@Work public void exec() {got();}
		}
		final A a = new A();
		class B extends PathTask {
			@Work public void exec() {
				class C extends PathTask {
					@Work public void exec(A a) {got(a);}
				}
				C c = new C();
				PathTask taskC = track.work(c).exp(a);
			}
		}
		B b = new B();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		verify(1);
	}

	@Test
	public void testNestedParllelization() {
		class A extends PathTask {
			private boolean it = false;
			@Work public void exec() {it = true;}
			boolean isIt() {return it;}
		}
		class B extends PathTask {
			@Work public void exec() {}
		}
		class C extends PathTask {
			@Work public void exec() {}
		}
		final A a = new A();
		final B b = new B();
		final C c = new C();
		PathTask taskA = track.work(a);
		PathTask taskB = track.work(b);
		PathTask taskC = track.work(c);

		class Record {
			long longWait = 0;
			long shortWait = 0;
		}
		final int LONG_WAIT = 50;
		final int SHORT_WAIT = 10;
		final Record record = new Record();
		track.work(new PathTask(){
			@Work public void exec(A a) {
				assertEquals(0,record.shortWait);
				assertEquals(0,record.longWait);
				class LongWaitDependsOnB extends PathTask {
					@Work public void exec(B b) {
						sleep(LONG_WAIT);
						got(b);
						record.longWait = System.currentTimeMillis();
					}
				}
				class ShortWaitDependsOnC extends PathTask {
					@Work public void exec(C c) {
						sleep(SHORT_WAIT); // sleep a little but less than LongWaitDependsOnB
						got(c);
						record.shortWait = System.currentTimeMillis();
					}
				}
				if (a.isIt()) {
					LongWaitDependsOnB longWait = new LongWaitDependsOnB();
					ShortWaitDependsOnC shortWait = new ShortWaitDependsOnC();
					PathTask taskLongWait = track.work(longWait).exp(b);
					PathTask taskShortWait = track.work(shortWait).exp(c);
				}
			}
		});
		
		verify(2);

		assertEquals(0,record.shortWait);
		assertEquals(0,record.longWait);
		sleep(SHORT_WAIT+15);
		assertTrue(record.shortWait > 0);
		assertEquals(0,record.longWait);
		sleep(LONG_WAIT+15);
		assertTrue(record.shortWait > 0);
		assertTrue(record.longWait > 0);
	}
}



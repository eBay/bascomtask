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
		verify(0);
	}
}



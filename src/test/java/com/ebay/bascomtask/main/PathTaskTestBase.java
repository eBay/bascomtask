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
import java.util.concurrent.TimeUnit;

import org.junit.Before;

import com.ebay.bascomtask.main.Orchestrator;

public abstract class PathTaskTestBase {

	protected static void sleep(int millis) {
		PathTask.sleep(millis);
	}
	
	PathTask list(PathTask...tasks) {
		return new PathTask.ListPath(tasks);
	}
	
	PathTask list(List<? extends PathTask> tasks) {
		PathTask[] ta = new PathTask[tasks.size()];
		tasks.toArray(ta);
		return new PathTask.ListPath(ta);
	}

	protected static List<String> EMPTY_STRS = new ArrayList<>();
		
	/**
	 * For streamlining unit tests, an new instance is made available
	 * for each @Test. 
	 * @author brendanmccarthy
	 */
	class TaskTestWrapper {
		final Orchestrator orc = Orchestrator.create();
		final List<PathTask> tasks = new ArrayList<>();

		PathTask work(PathTask task) {
			task.taskInstance = orc.addWork(task);
			return add(task);
		}
		PathTask passThru(PathTask task) {
			task.taskInstance = orc.addPassThru(task);
			return add(task);
		}
		PathTask ignoreTaskMethods(PathTask task) {
			task.taskInstance = orc.addIgnoreTaskMethods(task);
			return add(task);
		}
		private PathTask add(PathTask task) {
			tasks.add(task);
			return task;
		}
		void check() {
			for (PathTask task: tasks) {
				task.check();
			}
		}
	}
	
	protected void verify(int expectThreads) {
		verify(expectThreads,true);
	}
	
	protected void verify(int expectThreads, boolean testNoThreads) {
		verify(expectThreads,expectThreads,testNoThreads);
	}
	
	protected void verify(int expectThreadsMin, int expectThreadsMax) {
		verify(expectThreadsMin,expectThreadsMax,true);
	}
	
	protected void verify(int expectThreadsMin, int expectThreadsMax, boolean testNoThreads) {
		//track.orc.execute(TimeUnit.SECONDS.toMillis(1L));
		track.orc.execute(TimeUnit.SECONDS.toMillis(99999L));
		track.check();
		sleep(2);
		int nt = track.orc.getNumberOfThreadsCreated();
		String msg = "Got " + nt + " threads out-of-range " + expectThreadsMin + ".." + expectThreadsMax;
		assertTrue(msg,nt>=expectThreadsMin && nt<=expectThreadsMax);
		if (testNoThreads) {
			assertEquals(0,track.orc.getNumberOfOpenThreads());
		}
	}
	
	protected TaskTestWrapper track;
	@Before
	public void init() {
		track = new TaskTestWrapper();
	}
}

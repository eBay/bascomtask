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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generator for repeatedly calling a target test and reporting the results.
 * @author brendanmccarthy
 */
public class Loader {
	
	public interface Runner {
		void run() throws Exception;
	}
	
	private class Pass {
		final int pass;
		//final int seconds;
		final double avg;
		final long tput;
		Pass(int pass, int seconds, double avg, long tput) {
			this.pass = pass;
			//this.seconds = seconds;
			this.avg = avg;
			this.tput = tput;
		}
	}
	
	private List<Pass> passes = new ArrayList<>();
	
	private void report(int runLoop, String fmt, Object...args) {
		System.out.printf("[LT#%d] ",runLoop);
		System.out.printf(fmt,args);
	}
	
	private static final long MNS = 1000000;

	private void run2(ExecutorService es, int runLoop, int targetDurationSeconds, int threadsPerLoop, final Runner runner) throws Exception {

		report(runLoop,"Load test invoked with targetDurationSeconds=%d, threadsPerLoop=%d%n",targetDurationSeconds,threadsPerLoop);

        runner.run();  // Warm up
        
        report(runLoop,"Warm-up completed, starting load test%n");

        long start = System.nanoTime();
        final long targetEndTime = start + targetDurationSeconds * MNS * 1000;
        int loops = 0;
        long time;
        final AtomicInteger exceptions = new AtomicInteger(0);

        while ((time=System.nanoTime()) < targetEndTime) {
        	loops++;
        	for (int i=1; i<threadsPerLoop; i++) {
        		es.execute(new Runnable() {
					@Override
					public void run() {
						try {
							runner.run();
						}
						catch (Exception e) {
							exceptions.incrementAndGet();
							e.printStackTrace();
						}
					}});
        	}

        	runner.run(); // Do after other threads started
        }

        long actuaDuration = time-start;
        double avg = (actuaDuration/(double)loops) / MNS;
        long throughput = loops * threadsPerLoop;

        report(runLoop,"Over %d seconds with threadsPerLoop=%d loops=%d tput=%d avg=%.2fms xs=%d%n",targetDurationSeconds,threadsPerLoop,loops,throughput,avg,exceptions.intValue());
        passes.add(new Pass(runLoop,targetDurationSeconds,avg,throughput));
    }

	private void runForEachThreadCount(int targetDurationSeconds, int maxThreadsPerLoop, Runner runner) {
		
		ExecutorService pool = Executors.newCachedThreadPool();
		
		int runLoop = 0;
		try {
			for (int i=0; i<maxThreadsPerLoop; i++) {
				run2(pool,runLoop++,targetDurationSeconds,i+1,runner);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			pool.shutdown();
		}
		for (Pass pass: passes) {
			System.out.printf("%d,%.2f,%d%n",pass.pass,pass.avg,pass.tput);
		}
	}
	
	/**
	 * Repeatedly invokes the target Runner in a loop for the specified duration. Within each
	 * loop, spawn the specified number of additional threads to also invoke Runner (without
	 * waiting for them to complete).
	 * @param targetDurationSeconds how long to run for
	 * @param maxThreadsPerLoop how many threads to use -- if 1 then no additional threads are used
	 * @param runner containing test code to invoke
	 */
	public static void run(int targetDurationSeconds, int maxThreadsPerLoop, Runner runner) {
		Loader loader = new Loader();
		loader.runForEachThreadCount(targetDurationSeconds, maxThreadsPerLoop, runner);
	}
}

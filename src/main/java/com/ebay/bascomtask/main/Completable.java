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

/**
 * A type with a target completionCount that tracks its completionCount, for the purpose of 
 * tracing but also to known when {@link #hasCompleted()}.
 * @author brendanmccarthy
 */
class Completable {
	// After this many invocations (of any task method) this object will be considered complete 
	private int completionThresholdCount = -1;

	// Number of actual invocations so far started
	private int started = 0;

	// Number of actual invocations so far completed
	private int completed = 0;
	
	private int computedThresholdAtLevel = -1;
	
	/**
	 * Has been initalized at the given level?
	 * @return
	 */
	boolean recomputeForLevel(int level) {
		if (computedThresholdAtLevel != level) {
			computedThresholdAtLevel = level;
			return true;
		}
		return false;
	}
	
	boolean isCompletable() {
		return completionThresholdCount > 0;
	}
	
	void startOneCall() {
		started++;
	}

	boolean completeOneCall() {
		completed++;
		return hasCompleted();
	}
	
	/**
	 * His {@link Completable#completeOneCall()} been called at least as many 
	 * times as our threshold?
	 * @return
	 */
	boolean hasCompleted() {
		return completed>=completionThresholdCount;
	}
	
	int getCompletionThreshold() {
		return completionThresholdCount;
	}

	/**
	 * Sets threshold which determines when this completeable item is completed
	 * @param tc
	 * @return true iff new value is more than old
	 */
	boolean setCompletionThreshold(int tc) {
		boolean result = tc > this.completionThresholdCount;
		this.completionThresholdCount = tc;
		return result;
	}

	String completionSay() {
		Completable oc = containingCompletable();
		String outerText = oc==null ? "" : " of " + oc.completionSay();
		return "s" + String.valueOf(started) + "/c" + completed + "/t" + completionThresholdCount + outerText;
	}
	
	Completable containingCompletable() {
		return null;
	}
}

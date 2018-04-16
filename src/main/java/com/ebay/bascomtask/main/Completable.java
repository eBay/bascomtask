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
	
	/**
	 * Has been initalized?
	 * @return
	 */
	boolean countHasBeenComputed() {
		return completionThresholdCount >= 0;
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

	void setCompletionThreshold(int tc) {
		this.completionThresholdCount = tc;
	}
	
	String completionSay() {
		Completable oc = containingCompletable();
		String outerText = oc==null ? "" : " of " + oc.completionSay();
		return String.valueOf(started) + '/' + completed + '/' + completionThresholdCount + outerText;
	}
	
	Completable containingCompletable() {
		return null;
	}
}

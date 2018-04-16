package com.ebay.bascomtask.exceptions;

/**
 * Conditions that occur prior to any task being processed.
 * @author brendanmccarthy
  */
@SuppressWarnings("serial")
public class InvalidGraph extends RuntimeException {

	public InvalidGraph(String message) {
		super(message);
	}

	/**
	 * A task cannot be executed because it has no call with all paramaters added.
	 * @author brendanmccarthy
	 */
	public static class MissingDependents extends InvalidTask {
		public MissingDependents(String message) {
			super(message);
		}
	}

	/**
	 * A circular reference has been detected in the graph.
	 * @author brendanmccarthy
	 */
	public static class Circular extends InvalidTask {
		public Circular(String message) {
			super(message);
		}
	}

	/**
	 * A task has more than one method that can fire.
	 * @author brendanmccarthy
	 */
	public static class MultiMethod extends InvalidTask {
		public MultiMethod(String message) {
			super(message);
		}
	}
}

package com.ebay.bascomtask.exceptions;

/**
 * The orchestration graph ended up in a state where it could not complete.
 * @author brendanmccarthy
  */
@SuppressWarnings("serial")
public class GraphStateException extends RuntimeException {

	public GraphStateException(String message) {
		super(message);
	}

	public static class OrphanedTasks extends GraphStateException {
		public OrphanedTasks(String message) {
			super(message);
		}
	}
}

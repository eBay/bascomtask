package com.ebay.bascomtask.exceptions;

/**
 * Generated during Orchestrator execution. Some tasks may have completed
 * and some may still be in progress.
 * @author brendanmccarthy
  */
@SuppressWarnings("serial")
public class RuntimeGraphError extends RuntimeException {

	public RuntimeGraphError(String message) {
		super(message);
	}

	public RuntimeGraphError(String message, Exception e) {
		super(message,e);
	}

	public static class Timeout extends InvalidTask {
		public Timeout(long ms) {
			super("Timed out after " + ms + "ms");
		}
	}
}

package com.ebay.bascomtask.exceptions;

/**
 * A task supplied to an archestrator cannot be processed.
 * @author brendanmccarthy
  */
@SuppressWarnings("serial")
public class InvalidTask extends RuntimeException {

	public InvalidTask(String message) {
		super(message);
	}

	public static class BadParam extends InvalidTask {
		public BadParam(String message) {
			super(message);
		}
	}
	
	public static class BadReturn extends InvalidTask {
		public BadReturn(String message) {
			super(message);
		}
	}

	public static class NotPublic extends InvalidTask {
		public NotPublic(String message) {
			super(message);
		}
	}

	public static class NameConflict extends InvalidTask {
		public NameConflict(String message) {
			super(message);
		}
	}
}

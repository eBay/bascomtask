package com.ebay.bascomtask.config;

import com.ebay.bascomtask.main.TaskMethodClosure;

public interface ITaskClosureGenerator {
	
	/**
	 * Provides closure for invoking calls. Customize invocation behavior
	 * by overriding this method and return subclass of <code>TaskMethodClosure</code>.
	 * Note that there is no guarantee that the <code>TaskMethodClosure</code> returned
	 * will be invoked; overriding subclasses should simply return an instance of
	 * the desired type.
	 * @see com.ebay.bascomtask.main.Orchestrator#interceptor(ITaskClosureGenerator)
	 * @see com.ebay.bascomtask.config.DefaultBascomConfig#getClass()
	 */
	TaskMethodClosure getClosure();
}

package com.ebay.bascomtask.config;

import com.ebay.bascomtask.main.TaskMethodClosure;

public interface ITaskClosureGenerator {
	
	/**
	 * Provides closure for invoking calls. Can be customized.
	 * @see com.ebay.bascomtask.main.Orchestrator#interceptor(ITaskClosureGenerator)
	 * @see com.ebay.bascomtask.config.DefaultBascomConfig#getClass()
	 */
	TaskMethodClosure getClosure();
}

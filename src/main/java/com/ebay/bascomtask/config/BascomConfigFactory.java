package com.ebay.bascomtask.config;

public class BascomConfigFactory {
	
	private static IBascomConfig bascomConfig = new DefaultBascomConfig();
	
	public static IBascomConfig getConfig() {
		return bascomConfig;
	}
	
	public static void setConfig(IBascomConfig config) {
		bascomConfig = config;
	}
}

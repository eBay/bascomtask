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
 * A call wrapped with its actual parameters.
 * @author brendanmccarthy
 */
class Invocation {

	private final Call.Instance callInstance;
	private final Object[] args;
	private boolean called = false;
	
	private static Object[] EMPTY_ARGS = new Object[0];
	
	Invocation(Call.Instance callInstance, Object[] args) {
		this.callInstance = callInstance;
		if (args==null) {
			this.args = null;
		}
		else {
			this.args = new Object[args.length];
			System.arraycopy(args,0,this.args,0,args.length);
		}
	}
	
	@Override
	public String toString() {
		if (callInstance == null) {
			return "<<no call instance>>";
		}
		else {
			String what = called?"called@":ready()?"ready@":"not-ready@";
			return what + callInstance.formatState();
		}
	}
	
	Call.Instance getCallInstance() {
		return callInstance;
	}
	
	private boolean ready() {
		if (called) return false;
		if (args == null) return false;
		for (Object next: args) {
			if (next==null) return false;
		}
		return true;
	}
	
	Object[] copyArgs() {
		if (args==EMPTY_ARGS) {
			return args;
		}
		else {
			Object[] copy = new Object[args.length];
			System.arraycopy(args,0,copy,0,args.length);
			return copy;
		}
	}

	Call.Instance.Firing invoke(Orchestrator orc, String context, boolean fire) {
		if (!ready()) {
			throw new RuntimeException("Invocation not ready: " + this);
		}
		called = true;
		return callInstance.invoke(orc,context,args,fire);
	}
}

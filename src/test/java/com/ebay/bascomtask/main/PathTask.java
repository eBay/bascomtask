package com.ebay.bascomtask.main;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.ebay.bascomtask.main.ITask;

/**
 * Provides test tasks the ability to verify actual invocations against expected invocations.
 * @author bremccarthy
 */
abstract class PathTask {
	private int sleepFor = 0;
	ITask taskInstance;
	private final List<List<PathTask.Arg>> exp = new ArrayList<>();
	private final List<List<PathTask.Arg>> got = new ArrayList<>();
	
	static class Arg {

	}
	
	static class SingleArg extends PathTask.Arg {
		final PathTask arg;
		SingleArg(PathTask arg) {
			this.arg = arg;
		}
		@Override public int hashCode() {return Objects.hash(arg);}
		@Override public boolean equals(Object x) {
			if (this==x) return true;
			if (x instanceof PathTask.SingleArg) {
				PathTask.SingleArg that = (PathTask.SingleArg)x;
				return this.arg == that.arg;
			}
			return false;
		}
		@Override public String toString() {
			return arg.toString();
		}
	}
	
	static class ListArg extends PathTask.Arg {
		final List<PathTask> args;
		ListArg(List<PathTask> args) {
			this.args = args;
		}
		@Override public int hashCode() {return Objects.hash(args);}
		@Override public boolean equals(Object x) {
			if (this==x) return true;
			if (x instanceof PathTask.ListArg) {
				PathTask.ListArg that = (PathTask.ListArg)x;
				if (this.args.size() != that.args.size()) return false;
				if (!this.args.containsAll(that.args)) return false;
				if (!that.args.containsAll(this.args)) return false;
				return true;
			}
			return false;
		}
		@Override public String toString() {
			return css(args,PathTask.class);
			//return "{" + args.stream().map(Object::toString).collect(Collectors.joining(",")) + "}";
		}
	}
	
	@Override
	public String toString() {
		return taskInstance.getName()+':'+exp.size()+':'+got.size();
	}
	
	String getName() {
		return taskInstance.getName();
	}
	
	PathTask name(String name) {
		taskInstance.name(name);
		return this;
	}
	
	PathTask noWait() {
		taskInstance.noWait();
		return this;
	}
	
	PathTask multiMethodOk() {
		taskInstance.multiMethodOk();
		return this;
	}
	
	/**
	 * Sleeps for given number of ms when {@link #got} is called, in order to
	 * simulate taskInstance-specific delay.
	 * @param sleepFor ms
	 * @return
	 */
	PathTask sleepFor(int sleepFor) {
		this.sleepFor = sleepFor;
		return this;
	}
	
	PathTask.Arg asArg() {
		return new SingleArg(this);
	}
	
	static List<PathTask.Arg> toArgs(PathTask...tasks) {
		List<PathTask.Arg> args = new ArrayList<>();
		for (PathTask next: Arrays.asList(tasks)) {
			args.add(next.asArg());
		}
		return args;
	}
	
	PathTask exp(PathTask...tasks) {
		if (tasks.length > 0) {
			List<PathTask.Arg> args = toArgs(tasks); 
			//List<PathTask.Arg> args = Arrays.asList(tasks).stream().map(t -> t.asArg()).collect(Collectors.toList());
			exp.add(args);
		}
		return this;
	}
	
	PathTask got(PathTask...tasks) {
		if (tasks.length > 0) {
			List<PathTask.Arg> args = toArgs(tasks); 
			got.add(args);
		}
		if (sleepFor > 0) {
			sleep(sleepFor);
		}
		return this;
	}
	
	String fmt(List<PathTask.Arg> args) {
		return "(" + css(args,PathTask.Arg.class) + ")"; 
		//return "(" + args.stream().map(Object::toString).collect(Collectors.joining(",")) + ")";
	}
	
	static <T> String css(List<T> os, Class<T> cls) {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<os.size(); i++) {
			Object next = os.get(i);
			if (i==0) sb.append(',');
			sb.append(next.toString());
		}
		return sb.toString();
	}

	/**
	 * Raises JUnit failures if actual results did not match expected results. 
	 */
	synchronized void check() {
		List<String> bad = new ArrayList<>();
		for (List<PathTask.Arg> next: exp) {
			if (!got.contains(next)) {
				bad.add("msng" + next);
			}
		}
		for (List<PathTask.Arg>next: got) {
			if (!exp.contains(next)) {
				bad.add("unxp " + next);
			}
		}
		if (bad.size() > 0) {
			fail(taskInstance.getName() + " " + css(bad,String.class));
		}
		// This might happen when task is called extra times with same argument(s)
		int expSize = exp.size();
		int gotSize = got.size();
		if (expSize != gotSize) {
			fail(taskInstance.getName() + " contents matched but exp.length " + expSize + " != got.length " + gotSize);
		}
	}
	
	/**
	 * Temporary holder of tasklist so we can create a ListArg. Only extends PathTask for convenience -- it would be
	 * probably be cleaner to have this and PathTask share a common base but this works well enough for test purposes. 
	 */
	static class ListPath extends PathTask {
		final List<PathTask> tasks;
		ListPath(PathTask...args) {
			this.tasks = Arrays.asList(args);
		}
		PathTask.Arg asArg() {
			return new ListArg(tasks);
		}
	}
	
	static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		};
	}
}
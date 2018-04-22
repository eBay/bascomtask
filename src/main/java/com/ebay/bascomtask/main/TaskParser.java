package com.ebay.bascomtask.main;

import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.ebay.bascomtask.annotations.PassThru;
import com.ebay.bascomtask.annotations.Scope;
import com.ebay.bascomtask.annotations.Work;
import com.ebay.bascomtask.exceptions.InvalidTask;

/**
 * Performs any pre-processing of tasks that can be shared across
 * orchestrator runs to avoid the expense of doing so each time.
 * @author brendanmccarthy
 */
class TaskParser {
	
	private static TaskParser taskParser = new TaskParser();

	private Map<Class<?>,Task> map = new HashMap<>();
	
	/**
	 * Returns the task representation of any java class, pre-processing it if
	 * it does not already exist in the cache.
	 * @param clazz
	 * @return
	 */
	static Task parse(Class<?> clazz) {
		return taskParser.parse2(clazz);
	}

	private synchronized Task parse2(Class<?> clazz) {
		Task task = map.get(clazz);
		if (task==null) {
			task = new Task(clazz);
			map.put(clazz,task);
			// ! Reparse only after map updated because of potential recursion back to this method
			parse(task);  
		}
		return task;
	}
	
	private void parse(Task task) {
		for (Method method: task.taskClass.getDeclaredMethods()) {
			Call call = null;
			Work work = method.getAnnotation(Work.class);
			if (work != null) {
				call = new Call(task,method,work.scope(),work.light());
				task.workCalls.add(call);
			}
			PassThru passThru = method.getAnnotation(PassThru.class);
			if (passThru != null) {
				// @PassThru methods are always consider 'light'
				call = new Call(task,method,Scope.FREE,true);   
				task.passThruCalls.add(call);
			}
			if (call != null) {
				method.setAccessible(true);  // Methods need not be public, allowing for local classes
				verifyAccess(method);
				int index = 0;
				Type[] genericParameterTypes = method.getGenericParameterTypes();
				Class<?>[] pt = method.getParameterTypes();
				for (int i=0; i<method.getParameterTypes().length; i++) {
					boolean isList = false;
					Type nextMethodParamType = genericParameterTypes[i];
					Class<?> nextMethodParamClass = pt[i];
					// If the parameter is a List<T>, treat it is a T but mark the parameter as a list 
					if (List.class.isAssignableFrom(nextMethodParamClass)) {
						isList = true;
						if (nextMethodParamType instanceof ParameterizedType) {
							ParameterizedType genericType = (ParameterizedType)nextMethodParamType;
							Type typeArg = genericType.getActualTypeArguments()[0];
							nextMethodParamClass = (Class<?>)typeArg;
						}
					}
					Task paramTask = parse2(nextMethodParamClass);
					if (paramTask.workCalls.size()==0 && paramTask.passThruCalls.size()==0) {  // TODO Orc param
						throw new InvalidTask.BadParam("Task method " + mn(method) + " has non-Task parameter of type " + nextMethodParamClass.getSimpleName());
					}
					Call.Param param = call.new Param(paramTask,index,isList);
					call.add(param);
					paramTask.backLink(param);
					index++;  // Increment for all params whether task or not
				}
			}
		}
	}
	
	static String mn(Method method) {
		return method.getDeclaringClass().getSimpleName() + "." + method.getName();
	}
	
	private void verifyAccess(Method method) {
		int mod = method.getModifiers();
		if (!Modifier.isPublic(mod)) {
			throw new InvalidTask.NotPublic("Task method " + mn(method) + " must be public");
		}
		Class<?> rt = method.getReturnType();
		if (rt != Void.TYPE && rt != Boolean.TYPE) {
			throw new InvalidTask.BadReturn("Task method " + mn(method) + " must return void or boolean");
		}
	}
}

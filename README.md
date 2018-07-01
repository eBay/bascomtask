# BascomTask
Implementing microservices often requires processing requests that reach out to multiple external sources such as other services or datastores. A common challenge in implementing such requests is running some of these operations in parallel while also ensuring strict dependency ordering. The mechanisms of object orientation can be a great aid for making this work more manageable: each work item is split into its own task object, then a means to wire them together and execute them is needed, often referred to as "task orchestration". Several benefits can be achieved by breaking work items into tasks in this way:

* Enforcing separation of concerns between tasks
* Unifying cross-cutting capabilities such as logging and exception handling across tasks
* Parallel execution among tasks

BascomTask is a lightweight task orchestration library that features:

* Auto-wiring 
* Conditional wiring
* Multiple instance support
* Dynamic graph modification
* Optimal thread management

Core design philosophies of BascomTask include: 

* Flexibility while maximizing the benefits of Java's strong typing
* Dependency decisions made locally within each task definition without that logic having to account for anything beyond its immediate inputs and outputs
* Affording the wiring logic to only focus on inclusion or exclusion of tasks

## Hello World
Any POJO can be a task. BascomTask looks for methods annotated with @Work and executes them:

```java
   class HelloWorldTask {
     @Work public void exec() {
  	   System.out.println("Hello World");
     }
   }
   Orchestrator orc = Orchestrator.create();
   orc.addWork(new HelloWorldTask());
   orc.execute();  // Invokes tasks and waits all results are ready
```

More usefully, several tasks would be involved, and @Work methods can take other tasks as arguments:

```java
   class HelloTask {
     String getMessage() {
       return "Hello";
     }
   }
   class WorldTask {
     private String msg;
     String getMessage() {
       return msg;
     }
     @Work public void exec() {
  	   this.msg = "World";
     }
   }
   class ConcatenatorTask {
     @Work public void exec(HelloTask helloTask, WorldTask worldTask) {
  	   System.out.println(helloTask.getMessage() + " " + worldTask.getMessage());
     }
   }
   Orchestrator orc = Orchestrator.create();
   orc.addWork(new HelloTask());
   orc.addWork(new WorldTask());
   orc.addWork(new ConcatenatorTask());
   orc.execute();
```
In the example above, HellTask has no @Work methods. When added to the orchestrator graph it is immediately made available to downstream tasks (ConcatenatorTask in this case). WorldTask has a @Work method with no arguments and is started right away. ConcatenatorTask is started once WorldTask is completed. BascomTask processes these tasks all in the calling thread as there is no point in spawning separate threads. If HelloTask were to instead have a @Work method like WorldTask, BascomTask would spawn a thread to execute either HelloTask or WorldTask in parallel while the calling thread executes the other. BascomTask is dataflow driven and attempts to only create create threads when there is opportunity for parallelism.

Though not illustrated above, each call to addWork() returns an ITask object upon which several fluent-style operations are available for additional customization. For example, each task can have a name which would then substitute for the class name in logging output. The call to Orchestrator.create() similarly has several customization options available.

## Variant and Optional Tasks
Sometimes it is desired to modify task behavior based on certain conditions. While such logic can simply be embedded within any task, it can also be useful to preserve the benefit of distinct task responsibility and provide different classes for different variant task behavior. If two variant tasks are logically related in terms of purpose and also produce the same result, then a common base class be introduced that downstream tasks can depend on. This approach is beneficial in terms of hiding the variation from downstream tasks. When it is more natural for a downstream task to be aware of and process the variants differently, then multiple @Work methods can be provided on the downstream task, each with different parameter combinations.

```Java
   class C {
     @Work public void exec(A a) {...}
     @Work public void exec(B b) {...}
   }
   Orchestrator orc =  Orchestrator.create();
   if (cond) orc.addWork(new A());
   else orc.addWork(new B());
   orc.addWork(new C()
   orc.fire();
```

While any of the techniques above can be used for _variant_ behavior between more than one task, often the need instead is conditionally enabling a _single_ task to be active or not. A common example is enriching a list of user data with full profile information, which might be expensive and is therefore offered as optional to the caller. BascomTask provides the ability to easily handle optionality within any task by defining @PassThru methods in addition to any @Work methods on a task. When a given task is added with addWork(), then only its @Work annotated method(s) is activated, but if added with addPassThru() then only its @PassThru annotated method(s) is activated. A @PassThru method implementation defines what to do when its POJO task is not active. Downstream tasks need not be aware of it whether it was active or passive as they always get an instance of the needed type. Commonly, @PassThru methods perform simple behaviors such as return a default or passing upstream parameters downstream with no or little change.

For convenience, the addConditionally() method is provided that chooses either addWork() or addPassThru() dependent on a provided condition, as done with task B in this example:

```Java
   Orchestrator orc =  Orchestrator.create();
   orc.addWork(new A());
   orc.addConditionally(new B(),some_boolean_condition);
   orc.addWork(new C());
   orc.fire();
```
@PassThru methods in general operate the same way as @Work methods, although fewer options apply. In particular, @PassThrough methods are expected to be light-weight and therefore do not merit their own thread. Sometimes that particular behavior is desired even with @Work methods, which can be achieved by supplying an additional @Work parameter: 

```Java
class MyTask {
  @Work(weight=Task.Weight.LIGHT)
  public void exec(...) {...}
}
```

## Multiple Instances
Task instances need not be limited to just one; sometimes it is desirable to add multiple instances of a task (likely initialized with different constructor arguments). When multiple instances of a task exist, receivers get fired for each. In the following example there are two instances of X:

```Java
class X {
	@Work public void exec() {...}
}
class Y {
	@Work public void exec(X x) {...}
}
Orchestrator orc = Orchestrator.create();
orc.addWork(new X());
orc.addWork(new X());  // A second X is added
orc.addWork(new Y());
orc.execute();
```
Here, Y.exec will be called twice, receiving each X instance in turn. Y will also fire twice, impacting further depending tasks (none in this example). This behavior can be controlled by setting the scope argument on @Work:

* @Work(scope=Scope.FREE) ... (default) calls are made in parallel, assumes task instance is stateless or internally thread-guarded
* @Work(scope=Scope.AGGREGATE)... calls are made in parallel but the task is only fired when all are complete 
* @Work(scope=Scope.SEQUENTIAL)... threads are queued and executed one at a time (much like Java 'synchronized) and the tasks only fires once 
* @Work(scope=Scope.DUP)... new instances are created for  N+1 calls made

For methods that take multiple arguments, the method will be fired with the cross product of all inputs.

Alternatively, a receiving task can elect to receive all parameter instances at once by asking for a list rather a single instance. The call will be invoked when all senders have fired:

```Java
class X {
	@Work public void exec() {...}
}
class Y {
	@Work public void exec(List<X> x) {...}
}
Orchestrator orc = Orchestrator.create();
orc.addWork(new X());
orc.addWork(new X());  // A second X is added
orc.addWork(new Y());  // Only invoked once with two Xs
orc.execute();
```

## Inline Conditional Wiring
In the examples so far, conditional wiring (e.g. adding variant tasks, addConditionally, etc.) in BascomTask is done up front prior to execute(). However, sometimes that conditionality depends on the the outcome of executing other tasks and thus cannot therefore all be done up front. One easy approach to this problem is to simply nest a new Orchestrator inside a task method controlled by an outer Orchestrator; there is no limit on nesting in this way. 

In addition, BascomTask allows dynamic extension of a single Orchestrator which can expose more potential parallelism. Consider first the following example using nested Orchestrators. Three independent roots A, B, and C as well as two tasks that depend on B and C _but_ those dependent tasks should only run based on an outcome from A:

 ```Java
class A {
	@Work public void exec() {...}
}
class B {
	@Work public void exec() {...}
}
class C {
	@Work public void exec() {...}
}
class DependsOnB {
	@Work public void exec(B b) {...}
}
class DependsOnC {
	@Work public void exec(C c) {...}
}
class Chooser {
	@Work public void exec(A a, B b, C c) {
		if (a.someConditionIsTrue()) {
			Orchestrator orc = Orchestrator.create();
			orc.addWork(b);
			orc.addWork(c);
			orc.addWork(new DependsOnB());
			orc.addWork(new DependsOnC());
			orc.execute(); 
		}
	}
}

Orchestrator orc = Orchestrator.create();
orc.addWork(new A());
orc.addWork(new B());
orc.addWork(new C());
orc.addWork(new Chooser());
orc.execute();
```
Notice that Chooser.exec will only run when the A, B, and C instances are available when in fact the DependsOnB instance can run when A and B are available, and DependsOnC can run when A and C are available. Using inline wiring accommodates this scenario directly by replacing Chooser with a version that depends only on A, here illustrated with the inline anonymous class _new Object(){...}_:

```Java
Orchestrator orc = Orchestrator.create();
orc.addWork(new A());
orc.addWork(new B());
orc.addWork(new C());
orc.addWork(new Object() {
  @Work void exec(A a) {
    if (a.someConditionIsTrue()) {
      orc.addWork(new DependsOnB());
      orc.addWork(new DependsOnC());
    }
  }
orc.execute();
```
Now, for example, DependsOnB can execute even if C has not yet completed. The same effect could still be achieved with a separate Chooser class, where the outer orchestrator can be retrieved by requesting one's own ITask instance as a parameter which is then injected for you:

```java
class Chooser {
	@Work public void exec(A a, B b, C c, ITask task) {
		if (a.someConditionIsTrue()) {
			Orchestrator orc = task.getOrchestrator();
			...
		}
	}
}
```


## Configuration

IBascomConfig can be subclassed to effect various global configuration and customizations within BascomTask. The easiest way to do that is to extend the existing DefaultBascomConfig implementation and selectively override any desired methods. Here is a simple example way to do that in Spring:

```java
@Component
public class MyBascomConfig extends DefaultBascomConfig {
	@PostConstruct
    public void init() {
        BascomConfigFactory.setConfig(this);
    }
	// override other methods as desired
}
```

Among other things, interceptors can be set on on task method calls. For example, extending the previous MyBascomConfig example with the following method will log start and end messages on all task method invocations:

```java
    private ITaskInterceptor taskInterceptor = new ITaskInterceptor() {
        @Override
        public boolean invokeTaskMethod(ITaskMethodClosure closure) {
            System.out.println("Start " + closure.getMethodFormalSignature());
            try {
                return closure.executeTaskMethod();
            }
            finally {
                System.out.println("End " + closure.getMethodFormalSignature());
            }
        }
    }

    @Override
    public ITaskInterceptor getDefaultInterceptor() {
        return taskInterceptor;
    }
```


## Comparison to Alternatives
BascomTask does not maintain persistent state and therefore is unlikely to be suitable for long-running and/or indeterminate-duration tasks (e.g. when human approval is required). It provides no GUI for state inspection, only Java libraries for programmers. Alternatives such as [Netflix Conductor](https://netflix.github.io/conductor/) or other third-party business process management tools for persistent, long running tasks. BascomTask is intra- and not inter-process: it provides a framework for making remote calls but is not aware of how those calls are made or managed within tasks. While there is no particular BascomTask limit on orchestration or task duration, the longer the duration the more the more important becomes the ability to persist state between failures. A BascomTask-driven result can be fit within a larger long-running process orchestrated by one of these other tools, each offering their respective strengths at different levels of granularity. 

ESB-style alternatives such as [Apache Camel](http://camel.apache.org/) and [Apache Mule](https://en.wikipedia.org/wiki/Mule_(software)) are also available that provide rich Domain Specific Languages (DSLs) for Enterprise Integration Patterns (EIPs). BascomTask has no DSL, and instead focuses on offering a rich set of features around task management that best leverages the Java language directly. It aims to do simple things in the simplest possible way, while providing rich task management support, when desired, while keeping within a simple programming framework.

On a smaller scale, reactive frameworks such as [RxJava](https://github.com/ReactiveX/RxJava) provide rich capabilities for managing synchronous or asynchronous streams. BascomTask in comparison is coarser grained: task A is available to task B only when it is finished. This is typical when aggregating microservices, because most services don't themselves return streaming responses. BascomTask could be selectively combined with a streaming frameworks where it makes sense, allowing the best of both worlds.

## Implementation

A model of internal data structures can be seen in [here](class_model.md).



---
## License Information
       
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


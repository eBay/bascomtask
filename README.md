# BascomTask
BascomTask is a library for running tasks with optional dependencies in parallel. Typical uses cases include at least some relatively expensive tasks, possibly with different task wirings based on various conditions. The foremost example is in processing page or microservice requests that must in turn reach out to other services and/or datastores.

## Basics 
Any POJO can be a task. Such POJOs can have @Work annotated methods (often just one) that can take other tasks as arguments:

```java
class MyTask {
  @Work public void exec(MyOtherTask x, SomeOtherTask y) {...}
}

```
The above MyTask.exec (the method name does not matter) will only be executed when its two task arguments have completed execution of their own @Work methods, or 'fired' (which by implication means that parameters of those methods will never be null). To make this execute simply instantiate an Orchestrator, add your tasks to it (in any order), and execute() it:

```Java
   Orchestrator orc = Orchestrator.create();
   orc.addWork(new MyOtherTask());
   orc.addWork(new SomeOtherTask());
   orc.addWork(new MyTask());
   orc.execute();  // Waits all results are ready
```
If SomeOtherTask and MyOtherTask do not depend on each other, they will be invoked in parallel. One will be run in the this thread (the thread that calls execute()) and the other will run in a spawned thread. BascomTask is data-flow driven, attempting to execute in parallel where it can, otherwise using existing threads for sequential execution. Java POJO tasks are automatically wired to parameters ot other tasks expecting objects of that type; there is no further work required to establish ordering or dependencies between tasks.


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
	@Work public void exec() {}
}
class Y {
	@Work public void exec(X x) {}
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
	@Work public void exec() {}
}
class Y {
	@Work public void exec(List<X> x) {}
}
Orchestrator orc = Orchestrator.create();
orc.addWork(new X());
orc.addWork(new X());  // A second X is added
orc.addWork(new Y());  // Only invoked once with two Xs
orc.execute();
```

## Configuration

There are relatively few ways needed to globally configure BascomTask, but where needed can be done with a custom implementation of IBascomConfig. A default singleton implementation of IBascomConfig is provided, but users can provide their own alternative implementation through BascomConfigFactory.

## Implementation

A model of internal data structures can be seen in [here](class_model.md).





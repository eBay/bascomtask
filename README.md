# BascomTask
Implementing microservices often requires managing multiple requests that reach out to multiple external sources such 
as other services or datastores. The mechanisms of object orientation can be a great aid for making this work more
manageable: each integration point can be split into its own task object. Several benefits accrue from this division 
of labor:

* Enforcing separation of concerns between tasks
* Unifying cross-cutting capabilities such as logging and exception handling across tasks
* Parallel execution among tasks that can be time-wise expensive on their own 

A means to wire together and execute these tasks is needed, often referred to as "task orchestration". A common 
challenge in making this work is specifying what can be run in parallel while ensuring strict dependency ordering.
Full management of Java threads is tedious and error prone. CompletableFutures are much better but can still lead
to considerable complexities. BascomTask is a lightweight task orchestration library alternative that features: 

* **Implicit dependency analysis** based on Java static typing -- dependencies are computed from method signatures without additional programmer effort 
* **Optimal thread management** that spawns threads automatically only when it is advantageous to do so
* **Lazy execution** that automatically determines what to execute while freely allowing the execution graph to be extended at any time 
* **Fully asynchronous operation** with complete integration with Java 8+ CompletableFutures
* **Smart exception handling and rollback** that is customizable and minimizes wasteful task execution
* **Extensible task execution wrappers** with pre-existing built-ins for logging and profiling


## Creating a Task
To work within BascomTask, a task class can be any Java class that (a) has an interface and (b) that interface 
extends the BascomTask _TaskInterface_. There are no method implementations required when extending TaskInterface, 
it is simply a marker interface used by the BascomTask framework that also provides some useful default operations
that will be explained later on. An example of a simple task interface and class with one method that takes and returns
its argument might be:

```java
    import com.ebay.bascomtask.core.TaskInterface;

    public interface IEchoTask extends TaskInterface<IEchoTask> {
        String echo(String s);
    }

    public class EchoTask implements IEchoTask {
        public String echo(String s) { return s; }
    }
```

Notice how the generic parameter to TaskInterface is the interface itself (IEchoTask in this example). There are no
other requirements or restrictions on task classes other than providing an interface like the above. Task methods can 
have any number and type of parameter, and there can be any number of task methods on a class. The task class may
be stateful or not, depending on programming needs.

## Invoking a Task
The example above can be invoked as follows. On the left, for reference, is how we would invoke the task using only
Java code. On the right the same task invocation is run through BascomTask:

| Line | Plain Java | With BascomTask |
| --- | ----------------------------- | --------------------- |
1 | ... | Orchestrator $ = Orchestrator.create();
2 | String msg =  new EchoTask().echo("hello"); | String msg = $.task(new EchoTask()).echo("hello");

The difference is that instead of invoking the task method directly on the user POJO, that POJO is first added to
a BascomTask Orchestrator and then invoked (we use '$' as the variable name for an Orchestrator, but that is just
a convention that makes it stand out more). The benefit for this extra effort, in this simple example, is only that 
the task is managed by the framework and facilities such as logging and profiling apply. 
The real gains come when we aim for parallel execution if tasks. 

## Parallel Hello World
The first step toward parallelism is to employ CompletableFutures as the medium of exchange among tasks, which
maximizes opportunities for mapping different tasks to different threads. This is illustrated in the following 
modification of the previous example that includes an additional task for combining space-separated strings. 
We could have simple added the _combine()_ method same _EchoTask_ (a single task can have multiple diverse methods), 
but for clarity they are separated:

```java
    public interface IEchoTask extends TaskInterface<IEchoTask> {
        CompletableFuture<String> echo(String s);
    }
    
    public class EchoTask implements IEchoTask {
        public CompletableFuture<String> echo(String s) { return complete(s); }
    }
    
    public interface ICombinerTask extends TaskInterface<ICombinerTask> {
        CompletableFuture<String> combine(CompletableFuture<String> a, CompletableFuture<String> b);
    }

    public class CombinerTask implements ICombinerTask {
        public CompletableFuture<String> combine(CompletableFuture<String> a, CompletableFuture<String> b) {
            return get(a) + ' ' + get(b);
        }
    }
```
This example makes use of _get()_ and _complete()_ methods, which are convenience methods defined in _TaskInterface_ 
for getting and setting values from/to CompletableFutures. They are convenient but not mandatory; examining their simple
implementations will make that apparent.

With the above task definitions in place, we can wire them together like this:

```java
   import com.ebay.bascomtask.core.Orchestrator;

   class MyService {
       String getMessage() {
           Orchestrator $ = Orchestrator.create();
           CompletableFuture<String> left = $.task(new EchoTask()).echo("Hello");
           CompletableFuture<String> right = $.task(new EchoTask()).echo("world");
           CompletableFuture<String> combine = $.task(new CombinerTask()).combine(left, right);
           return cmobine.get();
       }
   }
```
With this, the left and right echo tasks will be executed in parallel and when they both complete then the CombinerTask 
will be executed. This works because the actual execution does start until the _get()_ call. The framework works 
backward from that call, recursively determining all the tasks that are required to complete that call, then
initiates execution such that any task's inputs are executed prior to it being started.
Because of this lazy execution, it is not costly to create many tasks and only later determine 
which ones are needed; the framework will determine the minimal spanning set of tasks to actually execute, and then 
proceed in a dataflow-forward execution style, executing tasks once (and only when) all their inputs are available 
and completed. 

> Since the dependency analysis is determined from the method signatures themselves, it is not possible
to mistakenly execute a task before its parameters are ready.

## General Programming Model
1) Task methods on are activated on demand by a call to execute, get, or other access operation on a CompletableFuture 
   returned from a task method. 
2) Activation runs backward, activating that task method and all its predecessors (incoming arguments).
3) Task methods that are ready to fire, either because all their CompletableFuture inputs have already completed or
   because they have no CompletableFuture inputs, are collected. 
4) All except one are spawned to new threads and all are started (one is kept for execution by the processing thread). 
5) Completion runs forward. Each completion checks for each of its dependents whether that dependent has all its 
   arguments ready. All _those_ ready-to-fire task methods are collected.
6) Return to step 4. 

> Note without any extra programming effort, the framework determines what tasks can be executed in parallel.

The following diagram illustrates the thread flow among 12 task method invocations (circles) activated by a
get() call, with execution color-coded with the thread (there are 4 in this example) that executes them:

![Thread Flow](doc/thread_flow.svg)

The conventions in this diagram are:

* Circles are task method invocations
* Arrows are forward dependencies (the task method on the arrow end take the other task method's output as a parameter)
* Task methods that are executed have a thick outline
* Arrows have a thick line when they are the final argument that completes all the target inputs
* Yellow fill in a task-method circle indicate that a value was returned and no exception was generated
  (in the example above, there were no exceptions but there will be in a later diagram)
* A gray outline means the task method was never activated, so it was never executed
* Otherwise, the color codes represent threads, of which there are 4 above indicated by green, red, blue, and orange; 
  the calling thread in this example is green while the others are spawned by the framework

The incoming request comes through the green thread that calls _get()_ on task 12. To compute its result, all the
non-gray tasks (excluding 4 and 11) are required. The ones with no incoming paths (1, 2, and 3) are executed first,
and since there are three of them and only one thread (the green calling thread), two threads are spawned (red and
blue) to execute tasks 2 and 3. 

All the other tasks have input arguments that are all CompletableFutures in this example. As each of those arguments 
is completed, a check is made for all dependent tasks that have all their arguments completed and ready. Just 
one of the predecessor tasks, the last one to complete, will result in the dependent task firing. This is indicated 
by the think lines in the diagram above.

Task 5 has two incoming arguments, and assuming task 2 is slower than task 1 then task
5 cannot execute until task 2 is complete. The green thread, which is the main thread, waits. When task 5 completes
it feeds into 7 and 8 and assuming task 6 has completed then both of 7 and 8 are ready to execute. Since the red task
can only execute one of them it looks to spawn a thread but since the main thread is waiting it simply reuses that
thread, hence task 7 is executed by the green thread. The same need occurs upon completion of task 6, but since there is
no main thread waiting it spawns an orange 4th thread for task 10. The final task 12 is executed by the blue thread,
assuming it arrives as the last argument for task 12. The result is now available to return to the original green
thread caller.

### Options for Task Initiation
As in the previous examples, a task method is activated when its value is retrieved through a _get()_ call. Activating
a task method activates all its predecessors, recursively, if they have not already been activated. Activation can
also be done independently of retrieving a value by calling _execute()_ on an Orchestrator. That call can be useful in
the following scenarios:

* For activating multiple tasks at once (_execute()_ takes a vararg list of CompletableFutures). If, for example, you
  otherwise simply call f1.get() followed by a call f2.get(), f2 won't be activated until f1 completes. While there is
  no functional impact to doing that, it might not be as efficient as activating them both at the same time with 
  an _execute()_ call.
* For activating a task that you don't want to wait for, e.g. a background task.
* For activating a task that is otherwise hidden. This is a special case. In addition to  _get()_, any of the many
  methods on CompletableFuture, such as _thenApply()_, _applyToEitherAsync()_, and so on, will also implicitly activate
  their arguments. The one case where this does _not_ happen is _thenCompose()_ because its CompletableFuture argument
  is hidden in a lambda. For tha case, its argument should be activated explicitly by an _execute()_ call.

There is no harm in calling _execute()_ multiple times and/or calling both execute and get for a CompletableFuture. 
The execution graph can extended at any time in a thread-safe manner with any of these calls.

### External CompletableFutures
CompletableFutures that originate externally to BascomTask can be used as task method arguments just like any 
BascomTask-managed CompletableFutures. BascomTask-managed CompletableFutures can be used externally to BascomTask 
just like any other CompletableFuture. BascomTask thus fits naturally into any asynchronous execution scheme.

In this simple (though not very useful) example, a CompletableFuture from some other source is fed into a task and that
output through a thenApply function:
```
    CompletableFuture<Integer> fromSomewereElse = ..
    CompletableFuture<integer> result = $.task(new MyTask()).computeSomething(fromSomwhereElse))).thenAccept(v->doSomethingWith(v));
```
The CompletableFuture result will now reflect having applied MyTask.computeSomething to the fromSomewhereElse value.

## Conditional Execution

At times the choice of a task may vary based on some runtime condition. At the simplest
level, the condition is known during graph construction, allowing a simple choice of task implementations e.g.:

```
   boolean cond = ...
   CompletableFuture f1 = cond ? $.task(new Task1()).compute1(...) : $.task(new Task2()).compute2(...);
```
Sometimes, however, the condition itself must be executed by a task. While you can compute _cond_ by a _get()_ on
the task method the produces the boolean condition, that would block until completed, and you may not even want
the entire conditional block to be executed in the first place. That could be avoided by moving the conditional logic 
itself to its own task, but BascomTask already provides a convenience form for. Suppose we have a task 
_SomeConditionTask_ with a method that produces a boolean result, then the following can be applied:

```
   CompletableFuture<Boolean> cond = $.task(new SomeConditionTask()).computeCondition(...);
   CompletableFuture<Integer> firstChoice = $.task(new Task1()).compute1(...);
   CompletableFuture<Integer> secondChoide = $.task(new Task2()).compute2(...);
   CompletableFuture<Integer> resolved = $.cond(cond,firstChoice,secondChoice);
   int got = resolved.get();
```
In this example, once _computeCondition()_ completes, then either _Task1.compute1()_ or _Task2.compute2_ will be 
executed. Because the _resolved_ value is requested (through the _get()_ call), then _cond_ task is required, 
and once complete then either _Task1.compute_ or _Task2.compute_ is executed. 

While that call to _cond()_ efficiently avoids executing either the _then_ or _else_ choice that will not be used,
it may be more desirable in some cases to start either of those result tasks at the same time as the condition task 
so that when the condition is ready the result tasks are also ready or at least have already been started.
The _cond()_ call has an alternate form for this purpose that allows booleans to be specified indicating this intent:

```
   CompletableFuture<Integer> resolved = $.cond(cond,firstChoice,true,secondChoice,true);
```
The _true_ values in this example indicate that the tasks behind each of firstChoice and secondChoice should be 
started at the same time as the task behind _cond_.

## Function Tasks
Sometimes it is convenient to define a task simply with a lambda function, without having to write separate
a separate class for it. These can be Supplier or Consumer functions, as in the following Suppler lambda 
that return the value 1:

```
   CompletableFuture<Integer> t1 = $.fn(()->1);
```
The call creates the task which is just like any other user POJO task and then invokes its
task method to return a CompletableFuture result. The definition of this fn method Orchestrator is this:

```
    default <R> CompletableFuture<R> fn(Supplier<R> fn) {
        return fnTask(fn).apply();
    }
```
As can be seen, fnTask is the call to make to get the task itself, which you may want to do to apply common
methods on it such as giving it a name or changing its weight -- unlike regular tasks, function tasks are
'light' by default meaning a separate thread will not be spawned for them, but that can be changed as described
later in the configuration section. For must purposes, the simpler and shorter fn() call will likely be sufficient.

There are number of these methods that take different kinds of lambdas, for Supplier as well as Consumer lambdas.
For lambdas that take arguments these must be passed to the fn/fnTask methods in turn, as CompletableFutures. The 
following example takes a value returned from a POJO task method, the hardwired value three, and a BiFunction 
that multiplies them together: 

```
    CompletableFuture f1 = $.task(new MyTask()).computeSomeValue();
    CompletableFuture<Integer> task = $.fn(
            f1,
            ()->3,
            (x,y)->x*y);
```
Consumer functions (producing void results) are called with vfn/vfnTask rather than fn/fnTask. They produce a
CompletableFuture<Void> that _must_ be accessed (e.g. by get() or execute()) in order to make the function execute. 


### User Task Adaptors
A variation of the _task()_ call can adapt POJO tasks that do not have interfaces and/or do not take or 
return CompletableFutures, For example, assuming we have a simple POJO
```java
   class RetTask {
      int ret(int v) { return v; }
   }
```
Then we can adapt it for use within BascomTask like so:
```
   Cf<Integer> x = $.task( new RetTask().ret(1) );
```
The tradeoff, of course, is that the task execution is completely invisible to BascomTask. A variation exists that
exposes the task to BascomTask that is slightly more helpful for logging and debugging:
```
   Cf<Integer> x = $.task( new RetTask(), t->ret(1) );
```



## Exception Handling
Any exception thrown from a task method is propagated to callers or execute() or to any of the various CompletableFuture 
methods that return values. This occurs even if the exception is generated in any spawned thread, at any level of 
nesting, from predecessor to successor recursively. If you get an exception while trying to perform an operation on a
CompletableFuture, it means that either the immediate task method behind it, or one of its ancestors, generated tha 
exception.

It is sometimes desirable to take action when tasks generate exceptions, such as reverting the side effects of previous 
tasks and/or computing an alternative result. The fate() operation serves this purpose, accepting a variable list of 
CompletableFutures and returning true only when at least one of them generates an exception. The following, for example, 
returns an alternate value if either f1 or f2 have exceptions, but just returns f1 otherwise: 

```
   CompletableFutre<Integer> f1 = $.task(new MyTask1()).computeThing(...);
   CompletableFutre<Integer> f2 = $.task(new MyTask2()).computeOtherThing(...);
   CompletableFuture<Boolean> fate = $.fate(f1,f2);

   CompletableFuture<Integer> alt = $.task(new MyAltTask()).computeAlternate(...);

   f1 = $.cond(fate,alt,f1);
```

Reversion functions can be conditionally applied:

```
   MyTask1 task1 = new MyTask1();
   MyTask2 task2 = new MyTask2();
   CompletableFutre<Integer> f1 = $.task(task1).computeThing(...);
   CompletableFutre<Integer> f2 = $.task(task2).computeOtherThing(...);
   CompletableFuture<Boolean> fate = $.fate(f1,f2);

   if (fate.get()) {
       CompletableFuture<Void> r1 = $.task(task1).revertThing(f1);
       CompletableFuture<Void> r2 = $.task(task2).revertOtherThing(f2);
       $.execute(r1,r2);
   }   
   
```
Grouping task outputs in a fate() call indicates an intention that their success or failure is linked. The action you
take, if any, is up to you, but the framework does an additional bit of optimization: it attempts to prevent from
starting, if they have not already started, all tasks methods that feed into the fate() call. While the fate() call 
does not exit until all its arguments have been processed, unlike regular task methods it does not 
wait for all its arguments to complete -- it recognizes a fault on any of its arguments as soon as it occurs. 
Then, it recursively works backwards attempting to prevent any of its predecessors (from its complete set of arguments) 
from starting if they have not already done so. Since those CompletableFutures should be completed in some way 
(so that any potential readers will not indefinitely block), they are set to complete exceptionally with a 
TaskNotStartedException. Reversion logic need simply check the state of its argument, so for the example above
that might be something like this:

```
  public CompletableFuture<Void> revertThing(CompletableFuture<Integer> myOutoutput) {
    if (!myOutput.isCompletedExceptionally()) {
       // reversion logic here
    }
    return complete();
  }
```

### General Exception Handling Flow
When a task throws an exception:
1. That exception is recursively propagated to all descendents, except for any FateTasks (created by calls to fate()),
which are collected in a list
1. For every collected FateTask,
   1. For every input to that FateTask
      1. For each ancestor task (recursively) that is not executed 
         1. mark it as TaskNotCompleted and propagate recursively to its descendents stopping at any task that either
            1. Is already completed (normally or with some other exception)
            1. Is a FateTask
1. Process the collected FateTasks in the normal manner

The following illustrates the various impacts of exceptions on a complex graph. As before, the tasks methods that 
return a value without exception are filled in yellow. This includes the root tasks 1, 2, and 3 that start and complete 
normally. Task 5 generates an exception and, as a result, a number of other task are set to return an exception and
so do not have a yellow fill. Assuming that task 5 completes (by generating its exception) before task 4 is started, 
the following applies:

![Fault Flow](doc/fault_flow.svg)

In addition to the propagation of MyException to its descendent tasks 12 and 13, task 8 is a call to fate() which
works to cancel other tasks from starting. In this example, task 2 would have already started and may even be
completed so there is nothing to do there, but task 6 has not started. The fate logic works backward
and also finds that task 4 has not started. That is cancelled and set to return a TaskNotStartedException. 
As with any other exception, that task is propagated forward to its descendents. In this example, that results
in tasks 7 and 9 being cancelled; task 13 was already set to complete with the original exception.

With the exception propagation having completed, task 8 itself can now be activated. It has two descendents and,
as with normal execution flow, a thread is spawned (orange) to handle the second one. These feed into task 13,
but that task has already generated an exception and that outcome remains unchanged. The original caller to
get() will see a MyException thrown. Should any call, by this or other threads get() be made on any other 
task method output, the results are fixed: all the yellow-filled task methods will return a valid value while
all the others will throw an Exception of the indicated type.


## Configuration

Orchestrators have various configuration options for controlling or extending core behavior. These can be set
on an individual orchestrator or on GlobalConfig.getConfig() which will then copy all of its settings to 
orchestrators when they are created. The GlobalConfig configuration object can also be entirely replaced if desired,
allowing complete control over Orchestrator initialization.

### Customizing the Thread Spawning Strategy

The time and manner of thread spawning can be controlled at multiple levels. At the task level, a test method can
be annotated as @Light, in which case no thread is ever spawned for it. This is useful because the writer of the
task method usually has a pretty good idea about the performance cost of that method. If the task method
implementation is not reaching out to other systems or databases, it should probably be @Light.

During wiring, that (or the default value which is not @Light) can be overridden, either setting it to be light or 
forcing it to always require a new thread to be spawned:

```
  $.task(new MyTask()).light().exec(...);
  $.task(new MyTask()).runSpawned().exec(...);
```

At the Orchestrator and global levels, a SpawnMode can be set that affects all spawning decisions for that or
all Orchestrators. Thread spawning can be always or never used, or only under specified conditions. The role of the 
main (calling) thread can be controlled, whether to be used or reused in task execution. The default is
SpawnMode.WHEN_NEEDED, which is the behavior described earlier this document, but others are available such as
NEVER_SPAWN.

### Task Runners
The execution of each task method can be intercepted/decorated by adding (any number of) TaskRunners to an 
Orchestrator. You can write your own or use a built-in from the BascomTask library:

* LogTaskRunner for logging ingress/egress of tasks
* StatTaskRunner for collecting aggregate timing information across tasks
* ProfilingTaskRunner for generating execution profiles for an Orchestrator

There are several ways to add a TaskRunner:

* Directly to an Orchestrator
* To GlobalConfig.getConfig().first/lastInterceptWith(), in which case that same TaskRunner instance will 
  be added to all Orchestrators
* Add an initializer function to GlobalConfig.getConfig().initializeWith() to generate a new TaskRunner instance that
  will be added to all new Orchestrators
* Using ThreadLocalRunner which, in addition to the previous, stores the TaskRunner using TheadLocal storage
  for later access

ProfilingTaskRunner is a good choice for the latter options above, because it maintains state that is generally only
useful in the context of a single Orchestrator (LogTaskRunner, as a contrary example, maintains no state). The 
following example uses a ThreadLocalRunner to set a ProfilingTaskRunner initializer function in one method and 
retrieve it in another method and print its report:

```java
class MyProfiler {
    private final ThreadLocalRunners<ProfilingTaskRunner> runner = new ThreadLocalRunners<>();

    public static void init() {
        runner.firstInterceptWith(ProfilingTaskRunner::new);
    }
    
    public static void report() {
        ProfilingTaskRunner ptr = runner.getAndClear();
        if (ptr != null) {
            System.out.println(ptr.format());
        }
    }
}
```
Between the call to _init()_ and _report()_, if an Orchestrator is created then its profile will be printed in
the call to _report()_.

Also of note is that the configuration object returned by GlobalConfig.getConfig() can be replaced entirely, with an
instance from a custom class extending the existing configuration class or an entirely different one. A use case
that leverages this capability might be to pull configuration information from an external configuration store.

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


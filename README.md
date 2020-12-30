# BascomTask
Implementing microservices often requires managing multiple requests that reach out to multiple external sources such 
as other services or datastores. The mechanisms of object orientation can be a great aid for making this work more
manageable: each integration point can be split into its own task object. Several benefits accrue from this division 
of labor:

* Enforcing separation of concerns between tasks
* Unifying cross-cutting capabilities such as logging and exception handling across tasks
* Parallel execution among tasks

A means to wire together and execute these tasks is needed, often referred to as "task orchestration". A common 
challenge in making this work is specifying what can be run in parallel while ensuring strict dependency ordering.
Full management of Java threads is tedious and error prone. CompletableFutures are much better but can still lead
to considerable complexities. BascomTask is a lightweight task orchestration library alternative that features: 

* **Implicit dependency analysis** based on Java static typing -- dependencies are computed from method signatures without additional programmer effort 
* **Optimal thread management** that spawns threads automatically only when it is advantageous to do so
* **Lazy execution** that automatically determines what to execute while freely allowing the execution graph to be extended at any time 
* **Fully asynchronous operation** with complete integration with Java 8+ CompletableFutures
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
        CompletableFture<String> combine(CompletableFuture<String> a, CompletableFuture<String> b);
    }

    public class CombinerTask implements ICombinerTask {
        public CompletableFture<String> combine(CompletableFuture<String> a, CompletableFuture<String> b) {
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
           CompletableFuture<String> left = $.task(new EchoTask().echo("Hello"));
           CompletableFuture<String> right = $.task(new EchoTask().echo("world"));
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
and completed. Since the dependency analysis is determined from the method signatures themselves, it is not possible 
to mistakenly execute a task before its parameters are ready. 

The following diagram illustrates the thread flow among 11 tasks (circles) color-coded with the thread 
(there are 4 in this example) that executes them:

![Thread Flow](doc/thread_flow.svg)

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

## Initiation and Termination of Tasks
As in the previous examples, a task method is activated when its value is retrieved through a _get()_ call. Activating
a task method activates all its predecessors, recursively, if they have not already been activated. Activation can
also be done independently of retrieving a value by calling _execute()_ on an Orchestrator. This can be useful in
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


## Conditional Execution

At times the choice of a task may vary based on some runtime condition. At the simplest
level, the condition is known during graph construction, allowing a simple choice of task implementations e.g.:

```
   boolean cond = ...
   CompletableFuture f1 = cond ? $.task(new Task1()).compute1(...) : $.task(new Task2()).compute2(...);
```
Sometimes, however, the condition itself must be executed by a task. While this can be handled within a nested task,
BascomTask provides a convenience form for this case. Suppose we have a task _SomeConditionTask_ with a method that
produces a boolean result, then the following can be applied:

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
task classes. These can be Supplier or Consumer functions, as in the following Suppler lambda that return the value 1:

```
   CompletableFuture<Integer> t1 = $.fn(()->1).apply();
```
The call to _$.fn(()->1)_ creates the task which is just like any other user POJO task. In order to get its
CompletableFuture result, a method must be called on it which in this case is _apply()_.

In addition to simple Supplier and Consumer lambdas, the standard variations that take one or two arguments
can be used. These necessitate that those arguments also be passed in the _$.fn()_ call. The following example
takes a value returned from a POJO task method, the hardwired value three, and a BiFunction that multiplies 
them together:

```
    CompletableFuture f1 = $.task(new MyTask()).computeSomeValue();
    CompletableFuture<Integer> task = $.fn(
            f1,
            ()->3,
            (x,y)->x*y).apply();
```

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
Exceptions are propagated to any of the various CompletableFuture methods that return values. This occurs even if 
the exceptions are generated in any spawned thread, at any level of nesting. When an exception occurs, 
any in-progress tasks are left to complete but no new tasks across the involved Orchestrator will be allowed to start.

In the following, an exception is generated in task 4 _prior_ to task 3 even starting:

![Fault Flow](doc/fault_flow.svg)

If one was to try and _get()_ a value from each these tasks, the result would be as follows:

* task1.get() ==> returns value
* task2.get() ==> returns value
* task3.get() ==> throws TaskNotStartedException
* task4.get() ==> throws MyException
* task5.get() ==> throws MyException


## Configuration

Various configuration options can be applied at multiple levels:

* For each task when being wired, and if not specified then
* For each Orchestrator, and if not specified then
* Globally for all Orchestrators (_GlobalConfig_)

This includes TaskRunners which can be used as decorators/interceptors that can be applied to each task method
invocation for such purposes as logging, statistics gathering, or profiling. BascomTask provides built-in versions 
of these (_LogTaskRunner_, _StatTaskRunner_, and _ProfilingTaskRunner_ respectively), but you can write your own
as well.

### Customizing the Thread Spawning Strategy

The time and manner of thread spawning can be controlled at multiple levels. At the task level, a test method can
be annotated as @Light, in which case no thread is ever spawned for it. This is useful because the writer of the
task method usually has a pretty good idea about the performance cost of that method, and in many cases if the
task method is not reaching out to other systems or databases it should probably be @Light.

During wiring, that (or the default value which is not @Light) can be overridden, either setting it to be light or 
forcing it to always require a new thread to be spawned:

```
  $.task(new MyTask()).light().exec(...);
  $.task(new MyTask()).runSpawned().exec(...);
```

At the Orchestrator and global level, a SpawnMode can be set that affects all spawning decisions for that or
all Orchestrators. Thread spawning can be always or never used, or only under specified conditions. The role of the 
main (calling) thread can be controlled, whether to be used or reused in task execution.

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


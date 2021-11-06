# BascomTask Changes Since Version 1

## v2.0
1. Significant rewrite that dropped support for implicit argument matching in favor of direct wiring.

## v2.1
1. Renamed 'execute' (now deprecated) to 'activate' methods on Orchestrator in order to clarify concepts.
2. Added Orchestrator.activateFuture(), which facilitates working asynchronously with a uniform set of items.
3. Added a variant of Orchestrator.activateWait() that works likewise with a uniform set of items.
4. Added Orchestrator.activateAsReady() for streaming results.   
5. Limited activation on CompletableFutures to value-access methods only, such as get(), join().
6. Added activate() methods on Orchestrator and TaskWrapper for easy explicit activation.
7. Replaced ThreadLocalRunner with LaneRunner which for managing TaskRunners works more consistently and easily against asynchronous execution.
8. Return parameterized Optional from cond/2 rather than void.
9. Added Orchestrator.current() for accessing current Orchestrator from within a task method.

## v2.2
1. Exposed isLight on TaskRun so runners can make decisions based on this property

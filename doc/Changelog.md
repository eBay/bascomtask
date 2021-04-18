# BascomTask Changes Since Version 1

## v2.0
1. Significant rewrite that dropped support for implicit argument matching in favor of direct wiring.

## v2.1
1. Renamed 'execute' (now deprecated) to 'activate' methods on Orchestrator in order to clarify concepts.
1. Added Orchestrator.activateFuture(), which facilitates working asynchronously with a uniform set of items.
1. Added a variant of Orchestrator.activateWait() that works likewise with a uniform set of items.
1. Added Orchestrator.activateAsReady() for streaming results.   
1. Limited activation on CompletableFutures to value-access methods only, such as get(), join().
1. Added activate() methods on Orchestrator and TaskWrapper for easy explicit activation.
1. Replaced ThreadLocalRunner with LaneRunner which for managing TaskRunners works more consistently and easily against asynchronous execution.
1. Return parameterized Optional from cond/2 rather than void.

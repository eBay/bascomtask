# BascomTask Changes Since Version 1

## v2.0
1. Significant rewrite that dropped support for implicit argument matching in favor of direct wiring.

## v2.1
1. Added Orchestrator.executeFuture(), which facilitates working asynchronously with a uniform set of items
1. Added a variant of Orchestrator.executeWait() that works likewise with a uniform set of items.
1. Added Orchestrator.executeAsReady() for streaming results.   
1. Replaced ThreadLocalRunner with LaneRunner which for managing TaskRunners works more consistently and easily against asynchronous execution.
1. Return parameterized Optional from cond/2 rather than void
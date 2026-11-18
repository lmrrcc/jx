# Java application authoring and browser execution

Java-only application authoring does not imply that browsers run JVM bytecode.
Resolve the intended execution model before implementing a new Java UI runtime;
preserve an established model unless the task calls for changing it.

| Candidate | Decisions to make | Evidence |
| --- | --- | --- |
| Server-executed components with browser HTML/updates | Latency, per-session memory, transport, auth, disconnect/reconnect, offline limitations | Java component updates in a browser; sessions stay isolated; disconnect releases state |
| Java compiled to JavaScript or WebAssembly | Supported JDK subset, dependency compatibility, reflection, debugging, payload size, server calls | Real compilation/browser test; useful unsupported-feature diagnostics; no secrets in assets |
| A deliberate combination | State ownership, serialization, component identity, hydration, duplicate effects | A concrete requirement for both models and verified boundaries |

Select one initial model using the product requirements, Java developer experience,
runtime limits, and maintenance cost. Generated assets or a minimal browser bridge
can preserve Java-only application authoring; distinguish source from generated
output and explain the build tools they require. Do not silently impose Node/npm
or a second authored language on application developers.

Record the choice, supported Java/browser/runtime versions, public consumer example,
failure semantics, deployment assumptions, and verification commands. Demonstrate
rendering, a state change, one server operation, invalid input, isolation, and
cleanup. Set performance/resource budgets from requirements and measurements,
not invented benchmarks or resemblance to React/Blazor.

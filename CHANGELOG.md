# Changelog

Notable changes to Capstead. Versions follow `0.MINOR.PATCH` while the library is pre-1.0: a **minor** bump
may change behaviour or a public shape, a **patch** never does.

## 0.8.0

### Breaking

- **`GET /actuator/capabilityexecutions/{id}` nests to full depth, and its `children` array changed shape.**

  ```diff
  -{ "execution": { … }, "children": [ { … }, { … } ] }
  +{ "execution": { … }, "children": [ { "execution": { … }, "children": [ … ] } ] }
  ```

  `children` now holds `ExecutionTree` objects rather than `CapabilityExecutionView` objects, so a consumer
  reading `children[].executionId` must read `children[].execution.executionId` instead.

  The response type has always been called `ExecutionTree` and previously returned one execution plus its
  **direct** children. A capability that called a capability that called another reported the middle layer and
  stopped, so a caller who did not know to walk it themselves received a complete-looking answer that was
  missing everything below depth one. The type now matches its name.

  An unknown or aged-out id still returns `null`, so callers already handling that case are unaffected.

- **`CapabilityExecutionQuery` gained `subtree(String executionId)`.** Any third-party implementation of that
  interface will not compile until it implements the new method. Both bundled implementations
  (`InMemoryCapabilityExecutionStore`, `JdbcCapabilityExecutionReader`) do.

### Added

- **`subtree(executionId)` — a whole execution tree in one call.** `childrenOf` answers a single level;
  reassembling a tree from it cost a round trip per level and left every caller writing the same traversal.
  In-memory this is one pass to index by parent then a breadth-first walk; over JDBC it is a recursive CTE,
  exercised against both H2 and MySQL 8.

  Ordering is part of the contract: the root comes first and every node appears after its own parent, so a
  consumer can build the nested shape in a single pass with no sorting and no second index. Siblings are
  most-recent-first, tie-broken on execution id so that executions recorded in the same millisecond — a
  capability fanning out to several tools does exactly that — still come back in a fixed order. Both
  implementations produce the same order, and a test asserts the property rather than a fixed list.

  Malformed graphs are bounded rather than fatal. Nothing validates that parent links form a tree, so a
  record whose ancestor claims it as a parent would otherwise loop forever on a read path serving an actuator
  endpoint: the in-memory store tracks visited ids, the CTE bounds depth at 50, and each returns a truncated
  tree instead of hanging. A duplicated id cannot detach already-attached children from the nested response.

- **A build.** `mvn -B -ntp verify` now runs on every pull request and push to `main`, on a runner with
  Docker, so the MySQL Testcontainers round-trips actually execute. They are
  `@Testcontainers(disabledWithoutDocker = true)` and therefore *skip* on a machine without a daemon, which
  meant a green local run could be several tests that never ran. The build reports totals and names any suite
  that skipped.

### Fixed

- **Test isolation in `JdbcCapabilityExecutionRoundTripTest`.** Every test opened the same H2 in-memory
  database under its default name and none closed it, so rows leaked between tests. Harmless while each test
  used unique ids, and the sort of failure that passes alone and fails together.

### Not included

`subtree` returns the same `CapabilityExecution` records the rest of `CapabilityExecutionQuery` returns
rather than a new export type. An export shape that consumers persist and diff is a contract worth settling
once, alongside the ordered execution events it will need to carry, so `findByAttribute` and a versioned
export shape remain open.

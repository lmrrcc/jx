# Persistence boundaries

- Use the application's established migration tool, or select one schema authority
  for a new application. Do not add both Flyway and Liquibase without a real need.
- Add forward migrations; do not rewrite applied migrations. Do not rely on
  Hibernate ddl-auto=update/create for persistent production data. Read-only SQL
  diagnosis is valid; mutations need the authorized migration/operational workflow.
- Design for populated databases: defaults, backfills, locks, indexes, constraints,
  and rollout order. Test upgrades from a previous schema, not only empty creation.
- Keep JPA entities out of public/library contracts. Use classes with appropriate
  constructors/proxy behavior; records are not JPA entities. Kotlin data-class
  generated equality/toString can traverse lazy relationships or mishandle identity.
- Decide equality for generated IDs and avoid logging lazy/sensitive associations.
  Match names to the actual schema and naming strategy. Explicit mappings are valid;
  do not assume Hibernate uppercases names or copy another project's schema policy.
- Bound queries, inspect fetch plans/N+1 behavior, and define cascade ownership,
  optimistic locking, and tenant/resource scope when relevant.
- Parameterize queries. Preserve absent versus explicit-null semantics in updates;
  a blanket ignore-null mapper can erase a legitimate clear operation.
- Use the actual database engine for its specific behavior, in disposable test
  infrastructure. Do not repoint routine tests at live databases when Docker or
  another local prerequisite is missing.
- Verify rollback leaves no partial state. Database rollback cannot undo an HTTP
  effect; reliable cross-system delivery needs a deliberate idempotency/outbox or
  equivalent design when the requirements call for it.

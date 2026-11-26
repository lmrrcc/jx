# Shared record wire contract

Status: focused implementation in progress; verification pending.

The [shared-contract proposal](../ga-contracts.md) describes the eventual API
surface. This first implementation proves one portable record through both an
existing Spring controller and a compiled browser consumer. It does not generate
arbitrary controllers, import arbitrary Jackson behavior, or expose private
server domain objects.

## Boundaries and acceptance

- `examples/todo-contract` owns the public `Todo` record and Todo operation paths.
  It has no Spring, Jackson, JDBC, browser or React runtime dependency. Both
  consumers compile the same record. The server retains UUID creation/parsing,
  validation of requests, transactions and optimistic conflict checks. Form
  drafts and private server models may differ from the transport value.
- A Maven browser-codec goal will inspect explicitly selected compiled public
  records through javac symbols, without loading application classes. Generated
  sources belong to the browser module. Unsupported types or serialization
  annotations fail generation instead of guessing a schema. Owned output and
  stale-source cleanup must not overwrite user files.
- The initial codec subset is required `String`, `boolean` and signed 32-bit
  `int`, matching Todo's actual Jackson wire contract. Generated read and write
  code invokes the canonical record constructor; record invariants remain active.
  JSON property names come from record components once, in declaration order.
- Separate exact-value vectors will establish `long` and decimal strings and
  explicit missing/null behavior against the real Spring endpoint and TeaVM in
  Chrome. Todo UUID identifiers remain strings. No exact value may pass through
  JavaScript Number as an intermediate representation.

M1-17 requires existing real Spring tests and the complete browser Todo example.
M1-19 additionally requires generated-code compilation, unsupported-type and
ownership tests, and the same real browser requests. M1-18 requires JVM and actual
browser/Spring vectors; a JVM-only codec test is insufficient.

Java 25 is the first shared artifact baseline. Older server JVMs, arbitrary custom
Jackson serializers/naming strategies, generated async operations, auth/session
integration, schema evolution and clean published-artifact onboarding remain
separate work. This does not mark the complete contract proposal as implemented.

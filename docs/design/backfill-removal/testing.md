# Testing — Remove the legacy agent classifier backfill

This change is a revert; the testing strategy is to prove the removal is
complete and caused no collateral breakage. It reuses the exact symbol names
from [architecture.md](./architecture.md) and the steps in
[implementation-plan.md](./implementation-plan.md).

## What the failing test was

`deployment contract test.legacyAgentClassifierBackfillUsesConcreteExecutionTimeBounds()`
reflected into a private method:

```
java.lang.NoSuchMethodException: embedded agent runtime service.backfillLegacyAgentExecutionClassifiers()
```

Per reviewer direction, the method must **not** be re-added. So this test has no
production counterpart to exercise and is removed entirely (see
`implementation-plan.md`, Step 2). There is no replacement test: the behavior it
covered no longer exists.

## Acceptance criteria

1. **Production code compiles without the feature.**

   ```bash
   ./gradlew embedded agent runtime module (compileJava)
   ```

   Passes with no reference to `IndexDAO`,
   `backfillLegacyAgentExecutionClassifiers`, `backfillVersionOf`,
   `reindexAgentExecutions`, `the agent classifier backfill version`, or
   `the agent classifier backfill version_VALUE`.

2. **Test sources compile without the reflection import.**

   ```bash
   ./gradlew :test-harness:compileTestJava
   ```

   Passes with `java.lang.reflect.Method` no longer imported.

3. **The previously failing suite passes.**

   ```bash
   ./gradlew :test-harness:test --tests \
     'deployment contract test'
   ```

   The suite runs green; the removed test is simply absent from the report.

4. **No regression elsewhere.**

   ```bash
   ./gradlew test
   ```

   Full suite passes. In particular, other `deployment contract test`
   methods that use `agentService.deploy(...)` / `agentService.start(...)` and
   `executionDAO` still run, confirming the retained fields survived the edit.

5. **Formatting is clean.**

   ```bash
   ./gradlew spotlessApply
   ```

## Grep-based completeness gate

Beyond compilation, confirm no dead references remain:

```bash
grep -rn "backfillLegacyAgentExecutionClassifiers" . || echo "clean"
grep -rn "the agent classifier backfill version"       . || echo "clean"
grep -rn "reindexAgentExecutions"                  . || echo "clean"
grep -rn "backfillVersionOf"                       . || echo "clean"
```

Each command must print `clean` (ignoring matches inside this `docs/design`
folder, which intentionally names the removed symbols).

## Bean-wiring smoke check

`AgentService` is a Spring `@Component` with `@RequiredArgsConstructor`.
Removing the `indexDAO` field changes the generated constructor signature.
Confirm Spring still instantiates the bean by relying on the existing
integration tests: any `@SpringBootTest`-based embedded agent runtime test that boots the
context (including `deployment contract test` itself) will fail
context startup if a required dependency were dropped incorrectly. Their green
status is the wiring proof — no dedicated new test is needed, and none should be
added, consistent with the "do not re-add the feature" directive.

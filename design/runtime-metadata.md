# Runtime metadata — task-declared secrets & env variables for polled tasks

> **Terminology:** the field is named `runtimeMetadata` (per stakeholder request), even
> though the values it carries are runtime parameters resolved from **secrets and/or
> environment-variable sources** — it is a single, source-neutral concept that covers
> **both secrets and environment variables**. A `TaskDef` declares the names it needs in
> `runtimeMetadata`; at poll time each name is resolved from the **secrets store first,
> then environment variables** (in that order) and the results are placed in
> `Task.runtimeMetadata`. The name is deliberately not "secrets" because a resolved value
> may come from either source — calling an env var a "secret" would be misleading.

**Status:** Design for review (draft PR)
**Base branch:** `feat/env-backed-secrets-and-environment` (this feature builds on that PR and reuses its DAOs)
**Origin:** `design/secrets.md` (`task_metadata` branch) — "Support for handling secrets in Tasks"

## 1. Summary

Workers often need sensitive values (API keys, LLM keys, tokens) to do their job. Today
the only way to hand a worker a managed secret is to embed a reference in the *workflow
definition*'s task input — `"apiKey": "${workflow.secrets.OPENAI_API_KEY}"` — which every
workflow using the task must repeat.

This feature lets a **TaskDef declare** the secret/environment names it needs. At **poll
time**, the server resolves those names and injects the resolved values into a dedicated
key→value field on the `Task` returned to the worker. The workflow definition stays clean;
the declaration lives once, on the task definition.

It **reuses** the `SecretsDAO` / `EnvironmentDAO` introduced by the base PR (env-backed by
default). It is **complementary** to — not a replacement for — the existing
`${workflow.secrets.X}` / `${workflow.env.X}` reference resolution.

## 2. Mechanism

```
TaskDef "llm_call":
    runtimeMetadata: ["OPENAI_API_KEY", "REGION"]  # new field: list of names

worker polls a "llm_call" task
  → for each declared name, resolve in order:
       1) SecretsDAO.getSecret(name)        (env-backed: CONDUCTOR_SECRET_<name>)
       2) EnvironmentDAO.getEnvVariable(name) (env-backed: CONDUCTOR_ENV_<name>)   [fallback]
  → returned Task carries a new field:
       runtimeMetadata: { "OPENAI_API_KEY": "sk-...", "REGION": "us-east-1" }   # name→value
```

Resolution reuses the base PR's providers; injection happens in `ExecutionService.poll`
(the same choke point the base PR already modified).

## 3. Design decisions (flagged for PR review)

These are the choices made where the origin doc was silent — call them out in review:

1. **Single list, secrets-then-env resolution.** `TaskDef.runtimeMetadata` is one
   `List<String>` of names; each name is tried against `SecretsDAO` first, then
   `EnvironmentDAO`. (Matches the doc: "Find them from 1) secrets store or 2) env variables
   in that order.") Not two separate lists.
2. **Wire-only, never persisted.** The resolved values are set only on the `Task` returned
   to the poller — never on the persisted `TaskModel`. Nothing lands in the datastore, UI,
   or execution history. (Consistent with the base PR's "secret value never persists"
   principle. `Task` and `TaskModel` are distinct classes, so this falls out naturally.)
3. **Missing names are omitted.** If a declared name resolves to `null` in both providers,
   it is left out of the map and a warning is logged (rather than injecting `null`).
4. **No new config.** The feature reuses the active providers. With
   `conductor.secrets.type=noop` and `conductor.environment.type=noop` it is inert
   (everything resolves to `null` → empty map).
5. **Field naming.** `TaskDef.runtimeMetadata` (declared names) and `Task.runtimeMetadata`
   (resolved name→value). Same field name on both classes (per stakeholder request), but
   different types: two layers — what the task *needs* vs what it *got*.
6. **JSON/REST only (no gRPC field).** `Task.runtimeMetadata` is serialized for REST pollers;
   it is **not** given a `@ProtoField` id, to avoid proto regeneration. gRPC pollers do not
   receive it in this version (OSS polling is predominantly REST/HTTP).
7. **Poll-only.** Only worker-polled tasks get injection. Server-side system tasks (which
   don't poll) are out of scope.

## 4. Model changes

### `common` — `TaskDef.java`
Add, mirroring the existing `inputKeys`/`outputKeys` treatment (field, getter/setter,
and inclusion in `equals`/`hashCode`; note `TaskDef.toString()` only renders `name`, so
`runtimeMetadata` — like `inputKeys`/`outputKeys` — is not added there):
```java
private List<String> runtimeMetadata = new ArrayList<>();
public List<String> getRuntimeMetadata() { return runtimeMetadata; }
public void setRuntimeMetadata(List<String> runtimeMetadata) { this.runtimeMetadata = runtimeMetadata; }
```

### `common` — `Task.java`
Add a wire-only resolved map. **Excluded** from `equals`/`hashCode` (ephemeral wire data,
not task identity/state); serialized only when non-empty:
```java
@JsonInclude(JsonInclude.Include.NON_EMPTY)
private Map<String, String> runtimeMetadata = new HashMap<>();
public Map<String, String> getRuntimeMetadata() { return runtimeMetadata; }
public void setRuntimeMetadata(Map<String, String> runtimeMetadata) { this.runtimeMetadata = runtimeMetadata; }
```
(No `@ProtoField` — see decision 6.)

## 5. Resolution component

New `core` component `RuntimeMetadataResolver` (package `com.netflix.conductor.core.secrets`),
reusing both DAOs — small, single-purpose, unit-testable:
```java
@Component
public class RuntimeMetadataResolver {
    private final SecretsDAO secretsDAO;
    private final EnvironmentDAO environmentDAO;
    // constructor injection

    /** Resolve each declared name (SecretsDAO first, EnvironmentDAO fallback); omit misses. */
    public Map<String, String> resolve(List<String> names) {
        Map<String, String> out = new LinkedHashMap<>();
        if (names == null) return out;
        for (String name : names) {
            String value = secretsDAO.getSecret(name);
            if (value == null) value = environmentDAO.getEnvVariable(name);
            if (value != null) out.put(name, value);
            else LOGGER.warn("Declared secret/env '{}' not found in secrets store or environment", name);
        }
        return out;
    }
}
```
Both DAO beans always exist (env or noop), so injection is unconditional.

## 6. Injection point

In `ExecutionService.poll(...)`, where the outgoing wire `Task` is built (right beside the
base PR's `substituteSecrets` call), populate the new field from the task's definition:
```java
Task task = taskModel.toTask();
task.setInputData(parametersUtils.substituteSecrets(task.getInputData())); // existing
taskModel.getTaskDefinition()
        .map(TaskDef::getRuntimeMetadata)
        .map(runtimeMetadataResolver::resolve)
        .ifPresent(task::setRuntimeMetadata);                               // new
tasks.add(task);
```
`taskModel.getTaskDefinition()` is already available and used in `poll`. The persisted
`TaskModel` is untouched. `RuntimeMetadataResolver` is added as a constructor dependency of
`ExecutionService`.

## 7. Security

- Resolved values live only on the outgoing `Task`; the persisted `TaskModel` never holds
  them → nothing in the datastore/UI/history.
- Same output-leakage caveat as the base PR: if a worker echoes an injected secret into its
  task output, that output is persisted — the worker's responsibility. There is intentionally
  no masking in this version (consistent with the base PR's documented limitation).
- The prefix boundary from the base PR still applies: env-backed providers only read
  `CONDUCTOR_SECRET_*` / `CONDUCTOR_ENV_*`, so a TaskDef cannot name an arbitrary process
  env var.

## 8. Non-goals (this version)

- gRPC (`@ProtoField`) support for `Task.runtimeMetadata`.
- Injection for server-side system tasks (only worker poll).
- Per-name source override, aliasing (map to a different key), or "required/optional"
  semantics — a declared name simply resolves or is omitted.
- Validation of declared names against a schema.
- Masking of injected values in worker-produced output.

## 9. Testing

- **Unit — `RuntimeMetadataResolverTest`:** secrets-store hit; env fallback hit; secrets take
  precedence over env for the same name; missing name omitted; null/empty list → empty map.
  (Driven via `System.setProperty` per the base PR's DAO test pattern.)
- **Unit — `ExecutionServiceTest`:** `poll` injects the resolved map onto the outgoing
  `Task` from the task definition's declared names, and the persisted `TaskModel` carries
  no runtime metadata.
- **Unit — model:** `TaskDef` round-trips `runtimeMetadata` (getter/setter, equals/hashCode);
  `Task.runtimeMetadata` serializes only when non-empty and is excluded from `equals`.
- **Integration — `test-harness` Spock spec:** register a `TaskDef` with
  `runtimeMetadata: ["API_KEY"]` (+ `CONDUCTOR_SECRET_API_KEY` / `CONDUCTOR_ENV_...` set via
  system properties), start a workflow, poll the task, assert
  `polled.runtimeMetadata["API_KEY"] == <value>` and the persisted task has an empty
  runtime-metadata map. Add an env-fallback case.

## 10. Open questions for review

- Unified list with secrets→env fallback into one `runtimeMetadata` map (current) vs. splitting
  secrets and environment into separate declaration lists / result maps (which would preserve
  the base PR's sensitive-vs-non-sensitive distinction for this path).
- Should a declared-but-missing name be a silent omission (current) or surface as a task/poll
  warning visible to the operator beyond the server log?
- Is REST-only acceptable for v1, or is gRPC field support required?

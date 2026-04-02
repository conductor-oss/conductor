# WAIT_FOR_WEBHOOK Precision Pass — Session Notes

**Date**: 2026-03-25
**Branch**: `feat/wait-for-webhook-foundation`
**PR**: #894

---

## What this session did

This session was a systematic side-by-side comparison of the OSS `webhook-task` module against
the Orkes Enterprise implementation. Every class was read in both codebases and compared for
behavioral fidelity. Six real bugs/gaps were found and fixed across two commits.

---

## Files read from Orkes Enterprise

| File | Purpose |
|---|---|
| `webhooks/.../WebhookHashingService.java` | Hash computation — inbound event |
| `core/.../WebhookTaskService.java` | DAO interface for task registration |
| `core/.../WebhookDAO.java` | DAO interface for config + matchers |
| `redis-persistence/.../RedisWebhookTaskService.java` | Redis DAO impl — hash format source of truth |
| `postgres-persistence/.../PostgresWebhookTaskService.java` | Postgres DAO impl — confirms hash format |
| `postgres-persistence/.../PostgresWebhookDAO.java` | Postgres DAO impl — matcher key format |
| `webhooks/.../IncomingWebhookService.java` | Inbound event handling |
| `webhooks/.../WebhookWorker.java` | Async task dispatch (Orkes queue-based path) |
| `webhooks/.../Webhook.java` | System task (start/cancel) |
| `webhooks/.../WebhookConfigService.java` | Config CRUD |
| `webhooks/.../IncomingWebhookResource.java` | REST endpoint |
| `server/.../WebhooksConfigResource.java` | Config management REST endpoint |
| `webhooks/.../WebhookVerifier.java` | Verifier interface |
| `common/.../WebhookConfig.java` | Config model |
| `common/.../IncomingWebhookEvent.java` | Event model |

---

## Bugs and gaps found

### Fix 1 — Task output format (`putAll` vs `put("payload", ...)`)
**Commit**: `fix: align WAIT_FOR_WEBHOOK output format with Orkes Enterprise`

**What was wrong**: `IncomingWebhookService.completeTask()` was writing:
```java
result.getOutputData().put("payload", payload);
```
So task output was `{ "payload": { "event": { "type": "..." }, ... } }`.

**Orkes does**:
```java
taskResult.getOutputData().putAll(payload);
```
So task output is `{ "event": { "type": "..." }, ... }` — payload keys at the top level.

**Impact**: Workflow variable references like `${waitForPayment.output.event.type}` would not
work in OSS. Users would have to write `${waitForPayment.output.payload.event.type}` — a
convention that doesn't exist in Orkes and would break any workflow migrated from Orkes to OSS.

**Files changed**:
- `IncomingWebhookService.java` — `putAll(payload)`
- `WaitForWebhookTask.java` — `putAll(payload)` in `complete()`, changed parameter type to
  `Map<String, Object>` (was `Object`)
- `IncomingWebhookServiceTest.java` — assert on actual payload key not wrapper
- `WaitForWebhookTaskTest.java` — assert keys directly in outputData
- `README.md` — update task output section and example

---

### Fix 2 — Secret leak in `getWebhook(id)`
**Commit**: `fix: precision pass — four Orkes convergence gaps in webhook-task`

**What was wrong**: `WebhooksConfigResource.getWebhook()` returned the config object directly,
including plaintext `secretValue`. `getAllWebhooks()` already masked secrets via `shallowCopy()`,
but the single-get path was missed.

**Orkes does**: `webhookConfig.setSecretValue(SECRET)` before returning (mutates the fetched
object, safe because Postgres always returns a fresh deserialized instance).

**OSS fix**: `config.shallowCopy()` then `masked.setSecretValue("***")` — using a copy so the
stored in-memory instance is not mutated.

**Files changed**:
- `WebhooksConfigResource.java` — shallowCopy + mask before return
- `WebhooksConfigResourceTest.java` — new test asserting (a) secret is masked and (b) original
  stored config is NOT mutated

---

### Fix 3 — NPE on null matcher criteria
**Commit**: `fix: precision pass — four Orkes convergence gaps in webhook-task`

**What was wrong**: `IncomingWebhookService.dispatchToWaitingTasks()` passed `criteria`
directly to `computeInboundHash()` with no null check. If a matcher map entry has a null value
(e.g. from a misconfigured DAO), `matchCriteria.keySet()` in `computeInboundHash()` would NPE.

**Orkes does**: explicit null check with log + `continue`.

**Files changed**:
- `IncomingWebhookService.java` — added `if (criteria == null) { LOGGER.warn...; continue; }`
- `IncomingWebhookServiceTest.java` — new regression test for null criteria

---

### Fix 4 — Task start order: DAO put before `setStatus(IN_PROGRESS)`
**Commit**: `fix: precision pass — four Orkes convergence gaps in webhook-task`

**What was wrong**: `WaitForWebhookTask.start()` set `task.setStatus(IN_PROGRESS)` first, then
called `webhookTaskDAO.put()`. If the DAO call throws (e.g. Redis is down), the task is already
marked `IN_PROGRESS` but is not registered in the DAO — it will never be completable by a webhook
event, and no error is visible.

**Orkes does**: `webhookTaskService.put(taskModel, workflow.getWorkflowVersion())` first, then
`taskModel.setStatus(IN_PROGRESS)`. If put fails, the exception propagates and the task status
is unchanged, allowing the engine to retry.

**Files changed**:
- `WaitForWebhookTask.java` — swapped order; comment explaining why

---

### Fix 5 — `isAsync()` returns `true` (should be `false`)
**Commit**: `fix: precision pass — four Orkes convergence gaps in webhook-task`

**What was wrong**: `WaitForWebhookTask.isAsync()` returned `true`, causing the Conductor decider
to periodically call `execute()` on every WAIT_FOR_WEBHOOK task. `execute()` always returns
`false` (the task is only completable via an external webhook event), so this polling is pure
overhead.

**Orkes does**: `Webhook.java` does not override `isAsync()`, so it inherits `false` from
`WorkflowSystemTask`. The task is never polled by the decider.

**Files changed**:
- `WaitForWebhookTask.java` — `isAsync()` returns `false` with explanatory Javadoc
- `WaitForWebhookTaskTest.java` — `isAsync_returnsTrue` renamed to `isAsync_returnsFalse`

---

## What was verified as correct (no change needed)

| Concern | Orkes source | OSS | Result |
|---|---|---|---|
| Hash format | `workflowName;version;taskRef;val1;val2` | identical | ✅ |
| Hash delimiter | `";"` constant in `WebhookTaskService.Constants` | `DELIMITER = ";"` | ✅ |
| Sort order | `new TreeSet<>(matches.keySet())` | identical | ✅ |
| JSONArray: first element only | `valueArray.get(0).toString()` | identical | ✅ |
| Wildcard: `expectedValue.startsWith("$")` | identical | ✅ | ✅ |
| Case-insensitive trim comparison | `equalsIgnoreCase + trim` | identical | ✅ |
| DO_WHILE suffix stripping | `TaskUtils.removeIterationFromTaskRefName()` | `stripIterationSuffix()` — same regex | ✅ |
| Matcher key: no strip in `computeMatchers()` | `task.getTaskReferenceName()` direct | identical (wf defs have no `__N` at definition time) | ✅ |
| Matcher key format | `workflowName;version;taskRefName` | identical | ✅ |
| DAO interfaces: no orgId params | Orkes impls read from `OrkesRequestContext` | same — isolation is impl's concern | ✅ |
| orgId call site | `OrkesRequestContext.get().setOrgId(TimeBasedUUIDGenerator.getOrgId(id))` | `orgContextProvider.applyContext(id)` before any DAO call | ✅ |
| `WebhookConfig` fields | all Orkes fields present | `name`, `id`, `receiverWorkflowNamesToVersions`, `workflowsToStart`, `urlVerified`, `sourcePlatform`, `verifier`, `headers`, `headerKey`, `secretKey`, `secretValue`, `createdBy`, `expression`, `evaluatorType` | ✅ |
| `@JsonIgnoreProperties(ignoreUnknown = true)` | present in Orkes | present in OSS | ✅ |
| `urlVerified` preserved on update | `webhookConfig.setUrlVerified(existing.isUrlVerified())` | identical | ✅ |
| Secret preserved when `"***"` sent on update | `webhookConfig.setSecretValue(existing.getSecretValue())` | identical | ✅ |
| Challenge/response — early return, skip dispatch | `if (webhookChallenge == null) { storeWebhook(...) }` | identical logic | ✅ |
| `workflowsToStart` reserved key removal | `wfsToStart.remove("idempotencyKey"); wfsToStart.remove("idempotencyStrategy")` | identical | ✅ |
| Non-integer version in `workflowsToStart` | skip with log | identical | ✅ |
| Workflow start failures isolated per-workflow | `catch(Exception e) { log; continue }` | identical | ✅ |
| `urlVerified` set on first successful verification | `if (!webhookConfig.isUrlVerified()) { ...; update = true }` | identical | ✅ |
| `urlVerified` set on successful ping | `if (response != null) { config.setUrlVerified(true) }` | identical | ✅ |
| `getWebhook(id)` masks secret | Orkes: `webhookConfig.setSecretValue(SECRET)` | OSS: shallowCopy + setSecretValue | ✅ (fixed) |
| getAllWebhooks masks secrets | Orkes: `webhookConfigs.forEach(w -> w.setSecretValue("***"))` | OSS: shallowCopy + setSecretValue per item | ✅ |

---

## Intentional behavioral differences (documented on PR)

### 1. Synchronous vs async processing
- **OSS**: tasks completed inline within the `POST /webhook/{id}` HTTP request
- **Orkes**: event stored to DB, pushed to `_webhook_queue`, consumed by `WebhookWorker` thread pool
- **Rationale**: OSS simplicity; queue infrastructure not part of OSS core deployment

### 2. `POST /webhook/{id}` — body is optional
- **OSS**: `@RequestBody(required = false)` — body defaults to `"{}"` if absent
- **Orkes**: `@RequestBody String bodyStr` — body required (implicit `required = true`)
- **Rationale**: deliberate OSS improvement — some providers (Stripe, GitHub) send POST pings
  with no body; the lenient approach handles more real-world cases

### 3. GET null ping response — dispatched (fixed)
- **Orkes**: when `verifier.handlePing()` returns `null` (not a recognized ping), Orkes creates
  an `IncomingWebhookEvent` with `body="{}"` and routes it through the full task-dispatch
  pipeline
- **OSS fix**: when `handlePing()` gets null from the verifier, it now calls
  `dispatchToWaitingTasks()` and `startWorkflowsToStart()` with an empty body and the
  query parameters as payload — same semantics as Orkes, inline rather than queued
- **Files changed**: `IncomingWebhookService.java`, `IncomingWebhookServiceTest.java`

### 4. Config not found — returns null (fixed)
- **Orkes**: records to dead-letter queue, returns `null` → HTTP 200
- **OSS fix**: `handleWebhook()` now returns `null` when config not found instead of throwing
  `IllegalArgumentException`. Avoids retry storms when a webhook config is deleted while the
  provider still has the URL registered.
- **Files changed**: `IncomingWebhookService.java`, `IncomingWebhookServiceTest.java`

---

## What the Orkes adapter layer needs to provide

To use this OSS module in Orkes Enterprise, three beans need to be registered:

### 1. `OrkesWebhookOrgContextProvider implements WebhookOrgContextProvider`
```java
@Bean
public WebhookOrgContextProvider orkesWebhookOrgContextProvider() {
    return webhookId -> {
        String orgId = TimeBasedUUIDGenerator.getOrgId(webhookId);
        OrkesRequestContext.get().setOrgId(orgId);
    };
}
```
Called by `IncomingWebhookResource` before any DAO access, for both `POST` and `GET` endpoints.

### 2. `OrkesRedisWebhookTaskDAO implements WebhookTaskDAO`
```java
public void put(String hash, String taskId) {
    orkesJedisProxy.sadd(nsKey("WEBHOOK_TASKS", "hash" + hash), taskId);
}
public List<String> get(String hash) {
    return new ArrayList<>(orkesJedisProxy.smembers(nsKey("WEBHOOK_TASKS", "hash" + hash)));
}
public void remove(String hash, String taskId) {
    orkesJedisProxy.srem(nsKey("WEBHOOK_TASKS", "hash" + hash), taskId);
}
```
The Redis key format `NAMESPACE:WEBHOOK_TASKS:hash<hash>` matches the existing
`RedisWebhookTaskService` exactly — same data, same keys, no migration needed.

### 3. `OrkesPostgresWebhookConfigDAO implements WebhookConfigDAO`
Wraps the existing Postgres queries from `PostgresWebhookDAO`, adding
`org_id = OrkesRequestContext.get().getOrgId()` filtering internally. The DAO interface has
no orgId parameters.

---

## Commits made in this session

```
0c6fc873c  fix: align WAIT_FOR_WEBHOOK output format with Orkes Enterprise
340c2bb67  fix: precision pass — four Orkes convergence gaps in webhook-task
cd6c83ca7  fix: precision pass — fix GET event dispatch and config-not-found behaviour
```

---

---

# Precision Pass 2 — Session Notes

**Date**: 2026-03-25
**Branch**: `feat/wait-for-webhook-foundation`
**PR**: #921 (OSS) + orkes-io/orkes-conductor#3535 (Orkes adapter)

## What this session did

A second side-by-side comparison pass using a subagent that read all 20+ files from both
codebases simultaneously. Two more convergence gaps found and fixed. Orkes adapter PR created.

---

## Bugs and gaps fixed

### Fix 6 — `handlePing()` dropping real events arriving via GET
**Commit**: `fix: precision pass — fix GET event dispatch and config-not-found behaviour`

**What was wrong**: When `verifier.handlePing()` returns `null` (verifier does not recognise the
request as a ping challenge), `handlePing()` returned null and did nothing. Some webhook providers
deliver real event data via GET query parameters; these events were silently dropped.

**Orkes does**: when `handlePing()` gets null from the verifier, creates an `IncomingWebhookEvent`
with `body="{}"` and the request params, then queues it in `_webhook_queue` for full processing
by `WebhookWorker`.

**OSS fix**: when `handlePing()` gets null response, calls `dispatchToWaitingTasks()` and
`startWorkflowsToStart()` inline with `body="{}"` and the request params. Same semantics, inline
rather than queued.

**Files changed**:
- `IncomingWebhookService.java` — else branch in `handlePing()` dispatches event
- `IncomingWebhookServiceTest.java` — `handlePing_nullResponse_treatedAsRealEvent` asserts
  dispatch path runs without error (no matching tasks registered, so no task completion,
  but path is exercised)

---

### Fix 7 — Config not found: throw 400 → return 200
**Commit**: `fix: precision pass — fix GET event dispatch and config-not-found behaviour`

**What was wrong**: `handleWebhook()` threw `IllegalArgumentException` (→ HTTP 400) when no
config was found for the given webhook ID.

**Orkes does**: records the event to a dead-letter queue, returns `null` → HTTP 200.

**OSS fix**: returns `null` immediately with a WARN log. HTTP 200 prevents retry storms when a
webhook config is deleted while the provider still has the URL registered. Most providers retry
400s with exponential backoff for hours.

**Files changed**:
- `IncomingWebhookService.java` — `return null` instead of `throw`
- `IncomingWebhookServiceTest.java` — `handleWebhook_returnsNullWhenConfigNotFound` (was:
  `expected = IllegalArgumentException.class`)

---

## Orkes adapter PR created

Branch `feat/oss-webhook-task-adapter` → `orkes-io/orkes-conductor#3535` (draft).

Three Spring beans bridging OSS interfaces to Orkes infrastructure:

| Bean | OSS interface | Implementation |
|---|---|---|
| `OrkesWebhookOrgContextProvider` | `WebhookOrgContextProvider` | `TimeBasedUUIDGenerator.getOrgId()` + `OrkesRequestContext.get().setOrgId()` |
| `OrkesRedisWebhookTaskDAO` | `WebhookTaskDAO` | `OrkesJedisProxy`, same Redis keys as `RedisWebhookTaskService` |
| `OrkesPostgresWebhookConfigDAO` | `WebhookConfigDAO` | `PostgresWebhookDAO`, Jackson round-trip for model conversion |

The OSS `@ConditionalOnMissingBean` guards in `WebhookAutoConfiguration` ensure these beans
automatically take precedence over the OSS no-op / in-memory defaults.

**Key compatibility facts verified:**
- `OrkesRedisWebhookTaskDAO` uses **exactly the same Redis key format** as the existing
  `RedisWebhookTaskService` (`NAMESPACE:WEBHOOK_TASKS:hash<hash>`). No data migration needed;
  existing data written by Orkes is readable by the new DAO.
- `OrkesPostgresWebhookConfigDAO` converts between OSS and Orkes `WebhookConfig` via Jackson
  round-trip. All common fields copy by name; Orkes-only fields (`tags`,
  `webhookExecutionHistory`) default to null in the OSS direction and are ignored in the Orkes
  direction (both have `@JsonIgnoreProperties(ignoreUnknown = true)`).
- Hash computation is identical between OSS `WebhookHashingService` and Orkes
  `WebhookHashingService` / `PostgresWebhookTaskService.computeHash()`. Same delimiter (`;`),
  same sort order (TreeSet on JSONPath keys), same wildcard rule (`startsWith("$")`), same
  case-insensitive trim comparison.

**Migration path after adapter PR merges**: disable/remove `io.orkes.conductor.webhook.rest.
IncomingWebhookResource`, `io.orkes.conductor.webhook.service.IncomingWebhookService`, and
`io.orkes.conductor.WebhookWorker`. The Orkes `WebhookConfigService` and
`WebhooksConfigResource` are replaced automatically via `@ConditionalOnMissingBean`.

---

## Remaining known gaps (deferred — not regressions)

| Gap | Orkes has | OSS | Notes |
|---|---|---|---|
| Async processing | `_webhook_queue` + `WebhookWorker` | Inline (sync) | Intentional OSS simplification |
| Audit logging | `EventMessage`, `EventExecution` | None | Enterprise feature |
| Webhook execution history | `WebhookExecutionHistory` on config | Not tracked | Enterprise feature |
| Tags on config | `List<Tag>` | Not present | Enterprise feature |
| `idempotencyKey` / `idempotencyStrategy` | Wired to `StartWorkflowRequest` | Keys removed, not used | Deferred — depends on `StartWorkflowInput` API |
| Additional verifiers | HMAC, Slack, Stripe, Twitter, SendGrid | HEADER_BASED + NONE | Future PRs |

---

# Final Precision Pass — Session Notes

**Date**: 2026-03-25
**Branch**: `feat/wait-for-webhook-foundation`

## What this session did

A final completeness pass focused on (1) fixing the last known validator gap, (2) adding a `NONE`
verifier for development/testing/demos, and (3) creating five demonstration workflow definitions
and five matching webhook config fixtures that collectively exercise every important
`WAIT_FOR_WEBHOOK` pattern.

---

## Bugs and gaps fixed

### Fix 8 — Null verifier bypasses validation, fails at event time with bad error
**What was wrong**: `validateWebhookConfig()` had no null check on `config.getVerifier()`. A
config with `"verifier": null` (or missing the field entirely) would pass `POST /api/metadata/webhook`
successfully. The failure came later, at the first inbound event, deep inside
`IncomingWebhookService.resolveVerifier()`, with `IllegalArgumentException: Webhook config id=X
has no verifier configured` — confusing, no hint how to fix it.

**Orkes does**: the Orkes REST layer always enforces that `verifier` is set (the field is
required in their UI/validation layer).

**OSS fix**: first check in `validateWebhookConfig()` now throws with a helpful message pointing
to `NONE` as the development option.

**Files changed**:
- `WebhooksConfigResource.java` — null check on `config.getVerifier()` at line 97
- `WebhooksConfigResourceTest.java` — `testCreateWebhook_validation_nullVerifier_noHeaderCheck`
  (was: passes) renamed to `testCreateWebhook_validation_nullVerifier_throws` (now: expects
  `IllegalArgumentException`); two other tests that had no verifier set now use `NONE`.

---

### Fix 9 — No NONE verifier (blocks demo/testing without credential setup)
**What was wrong**: Every `WebhookConfig` required either `HEADER_BASED` (secret headers) or
another credential-based verifier. It was impossible to demonstrate or test the system without
setting up authentication credentials. This also made it harder to build integration tests
(test-harness has no TLS/HMAC infrastructure).

**OSS fix**: Added `NONE` to `WebhookConfig.Verifier` enum + `NoOpVerifier` `@Component`:
- `verify()` always returns empty error list (all requests pass)
- `extractChallenge()` returns null (no challenge handshake)
- `handlePing()` returns null (treat GET requests as real events, not pings)

`NONE` is registered the same way as any other verifier — discovered by component scan, keyed by
`getType()` in `IncomingWebhookService.verifiersByType`. Enterprise deployments that want to
disable `NONE` in production can override the `WebhooksConfigResource` bean and add a check.

**Files changed**:
- `WebhookConfig.java` — `NONE` added as first enum constant with Javadoc warning
- `NoOpVerifier.java` — new `@Component` class implementing `WebhookVerifier`
- `WebhooksConfigResourceTest.java` — two test configs updated to use `NONE` where they
  previously had no verifier set

---

## Demonstration workflow definitions created

Five workflow definition JSONs in `test-harness/src/test/resources/`:

| File | Pattern | Key feature exercised |
|---|---|---|
| `wait_for_webhook_basic_test.json` | Literal match | Simplest case: `$['event']['type']` = `"order_fulfilled"` |
| `wait_for_webhook_per_instance_routing_test.json` | Wildcard / per-instance | `$['event']['orderId']` = `"${workflow.input.orderId}"` — each instance matches only its own event |
| `wait_for_webhook_sequential_test.json` | Sequential tasks | Two WAIT_FOR_WEBHOOK tasks in series; first for manager approval, second for director approval |
| `wait_for_webhook_workflowstostart_target_test.json` | workflowsToStart | Target workflow for `workflowsToStart` mode — started fresh by each event, no WAIT_FOR_WEBHOOK task |
| `wait_for_webhook_in_do_while_test.json` | DO_WHILE loop | WAIT_FOR_WEBHOOK inside a loop; exercises the `__N` iteration suffix stripping in hash computation |

Five webhook config fixture JSONs:

| File | Verifier | Mode |
|---|---|---|
| `webhook_config_basic_demo.json` | NONE | `receiverWorkflowNamesToVersions` |
| `webhook_config_per_instance_routing_demo.json` | NONE | `receiverWorkflowNamesToVersions` |
| `webhook_config_sequential_approvals_demo.json` | NONE | `receiverWorkflowNamesToVersions` |
| `webhook_config_workflowstostart_demo.json` | NONE | `workflowsToStart` |
| `webhook_config_in_do_while_demo.json` | NONE | `receiverWorkflowNamesToVersions` |

All configs use `"verifier": "NONE"` so they can be exercised in a running Conductor server
without any credential configuration.

---

## Verification at the end of this pass

```
./gradlew :conductor-webhook-task:test    → BUILD SUCCESSFUL (102 tests, 0 failed)
./gradlew :conductor-webhook-task:spotlessApply → clean
```

---

## Complete list of OSS fixes across all three sessions

| # | File | Change |
|---|---|---|
| 1 | `IncomingWebhookService`, `WaitForWebhookTask` | `putAll(payload)` instead of `put("payload", payload)` — flat output format |
| 2 | `WebhooksConfigResource` | `shallowCopy()` + mask before returning single webhook (prevent secret leak) |
| 3 | `IncomingWebhookService.dispatchToWaitingTasks()` | Null check on `criteria` before passing to hash service |
| 4 | `WaitForWebhookTask.start()` | DAO `put` before `setStatus(IN_PROGRESS)` — failure atomicity |
| 5 | `WaitForWebhookTask.isAsync()` | Returns `false` (was `true`) — prevent unnecessary decider polling |
| 6 | `IncomingWebhookService.handlePing()` | Dispatch real event when verifier returns null from handlePing |
| 7 | `IncomingWebhookService.handleWebhook()` | Return null (not throw) when config not found — prevent retry storms |
| 8 | `WebhooksConfigResource.validateWebhookConfig()` | Null verifier check with helpful error message |
| 9 | `WebhookConfig.Verifier` + `NoOpVerifier` | Added NONE enum value + NoOpVerifier implementation |

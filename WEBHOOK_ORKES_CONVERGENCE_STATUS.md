# WAIT_FOR_WEBHOOK: Orkes↔OSS Convergence Status

**Date:** 2026-03-25
**Branch:** `feat/wait-for-webhook-foundation`
**Context:** Viren raised concerns about (a) the OSS PR being a rewrite rather than a port, and (b) dependency conflicts when Orkes imports the OSS webhook-task module.

---

## TL;DR

The OSS PR is not a ground-up rewrite — it *is* the Orkes code with enterprise concerns stripped. The adapter foundation on the Orkes side is mostly done. The remaining work is:

1. Finish wiring the Orkes REST/worker layer to use the provider pattern (3–5 lines each)
2. Fix one critical dependency conflict (Jackson version floor) before Orkes can safely import the OSS JAR

---

## What the OSS PR Already Does

The `feat/wait-for-webhook-foundation` branch ports the Orkes implementation and strips enterprise-specific concerns:

| OSS Component | Status | Notes |
|---|---|---|
| `WebhookConfig` | ✅ Ported | `@JsonIgnoreProperties(ignoreUnknown=true)` — Orkes fields (tags, history) round-trip safely |
| `IncomingWebhookEvent` | ✅ Ported | Identical to Orkes model |
| `WebhookVerifier` interface | ✅ Ported | Returns `List<String>` instead of Orkes `ErrorList` |
| `WebhookOrgContextProvider` interface | ✅ New | No-op in OSS; Orkes implements with `TimeBasedUUIDGenerator` |
| `WebhookConfigDAO` interface | ✅ New | No orgId params — isolation is the *implementation's* concern |
| `WebhookTaskDAO` interface | ✅ New | Same pattern |
| `InMemoryWebhookConfigDAO` | ✅ New | OSS default (no org) |
| `InMemoryWebhookTaskDAO` | ✅ New | OSS default (no org) |
| `IncomingWebhookService` | ✅ Ported | **Synchronous** dispatch (see Architecture section) |
| `IncomingWebhookResource` | ✅ Ported | Calls `orgContextProvider.applyContext(id)` instead of hardcoding |
| `WebhookConfigService` | ✅ Ported | |
| `WebhooksConfigResource` | ✅ Ported | |
| `WebhookHashingService` | ✅ Ported | |
| `HeaderBasedVerifier` | ✅ Ported | |
| `WaitForWebhookTask` | ✅ New | Stores hash in task input for clean cancellation |
| `WaitForWebhookTaskMapper` | ✅ New | In `conductor-core`, uses `ParametersUtils` |
| `WebhookAutoConfiguration` | ✅ New | Spring auto-config with `@ConditionalOnMissingBean` guards |

**Not included (by design):** HMAC/Slack/Stripe/Twitter/SendGrid verifiers, async WebhookWorker, event audit history, tags — these are enterprise add-ons, not OSS scope.

---

## Orkes Adapter Layer: Current State

Orkes already has an `/oss/` adapter directory at:
`orkes-conductor/webhooks/src/main/java/io/orkes/conductor/webhook/oss/`

### What's Done

| File | Status |
|---|---|
| `OrkesWebhookOrgContextProvider` | ✅ Done — extracts orgId via `TimeBasedUUIDGenerator`, sets `OrkesRequestContext` |
| `OrkesPostgresWebhookConfigDAO` | ✅ Done — Jackson round-trip conversion, delegates to Orkes `WebhookDAO` |
| `OrkesRedisWebhookTaskDAO` | ✅ Done — uses `BaseDynoDAO`, same Redis key format as old `RedisWebhookTaskService` (no migration needed) |

All three are `@Component`-annotated; Spring auto-discovery replaces OSS defaults.

### What's NOT Done (the remaining friction)

**Orkes `IncomingWebhookResource`** (`io.orkes.conductor.webhook.rest`) still hardcodes orgId extraction:
```java
// Lines 63-64 and 70-71 in Orkes IncomingWebhookResource:
String orgId = TimeBasedUUIDGenerator.getOrgId(id);
OrkesRequestContext.get().setOrgId(orgId);
```
This should be deleted and the OSS `IncomingWebhookResource` used instead — which already calls `orgContextProvider.applyContext(id)`.

**Orkes `WebhookWorker`** (`io.orkes.conductor.WebhookWorker`) also hardcodes orgId at lines 167-168:
```java
String orgId = TimeBasedUUIDGenerator.getOrgId(messageId);
OrkesRequestContext.get().setOrgId(orgId);
```
Replace with an injected `WebhookOrgContextProvider.applyContext(messageId)`.

**Orkes `IncomingWebhookService`** is a separate Orkes implementation (async queue-based). This is intentional — Orkes keeps their own service. It does NOT need to use the OSS version.

**No explicit Spring `@Configuration` class** wires up the adapter beans — relying on classpath scanning. Consider adding one for explicitness.

---

## Architecture Difference: Sync vs Async

| | OSS | Orkes |
|---|---|---|
| **Dispatch** | Synchronous — completes tasks inline during HTTP request | Async queue — stores events, `WebhookWorker` processes in background |
| **Fan-out** | Works for < ~50 tasks; warns at threshold | Handles arbitrary fan-outs durably |
| **Infrastructure** | None beyond Spring | Redis/Postgres queue required |
| **Convergence** | Orkes keeps their `IncomingWebhookService` + `WebhookWorker` | Uses OSS models, DAOs, REST resource |

**The async service is an Orkes add-on, not something OSS needs to adopt.** Convergence means shared interfaces and REST layer, not shared dispatch model.

---

## Dependency Conflicts

When Orkes imports `conductor-webhook-task` as a JAR, the following conflicts arise:

### Critical: Jackson 2.17.0 (OSS) vs 2.15.2 (Orkes forced)

OSS root `build.gradle` forces all Jackson to 2.17.0. Orkes root `build.gradle` forces all Jackson to 2.15.2. When Orkes imports the OSS JAR compiled against 2.17.0 and then downgrades at runtime to 2.15.2, any API added in 2.16–2.17 causes `NoSuchMethodError`.

**Fix options:**
- **Option A (preferred):** Audit which Jackson APIs `webhook-task` actually uses; lower the floor to 2.15.x-compatible APIs. OSS webhook-task inherits 2.17.0 from the root build but may not need any 2.16+ features.
- **Option B:** Apply the Shadow plugin to `webhook-task` and shade Jackson under an internal namespace (same pattern as `os-persistence-v2`/`os-persistence-v3`).

### Medium: Protobuf 4.33.0 (OSS) vs 3.25.5 (Orkes)

OSS `conductor-common` pulls protobuf 4.33.0. Orkes pins to 3.25.5. Major version — binary incompatible in some paths.

**Fix:** Check whether `webhook-task` or `conductor-common` classes actually used by the webhook adapter generate or consume protobuf. If the webhook path never touches gRPC/proto serialization, this is dormant.

### Low: Log4j 2.23.1 (OSS) vs 2.17.1 (Orkes strictly pinned)

Orkes pins Log4j for supply chain policy reasons. Downgrade is safe if webhook-task uses only standard logging APIs.

### Low: Tomcat 10.1.45 (OSS) vs 10.1.35 (Orkes)

Patch-level difference. Binary compatible.

---

## Recommended Path Forward

### Step 1 — Fix the dependency floor (unblocks Viren immediately)

Run a quick audit of `webhook-task` Jackson usage:
```bash
cd conductor-oss/conductor
grep -r "import com.fasterxml.jackson" webhook-task/src/main/java/
```
If only standard annotations and `ObjectMapper` are used, constrain `webhook-task/build.gradle` to compile-compatible-with-2.15.x. Add a comment explaining why.

Alternatively, apply Shadow shading to `webhook-task` to isolate it completely.

### Step 2 — Finish Orkes REST/worker wiring (~1 hour)

In `orkes-conductor`:
1. Delete Orkes's own `IncomingWebhookResource` (or convert to `@ConditionalOnMissingBean`)
2. Replace hardcoded orgId extraction in `WebhookWorker` with injected `WebhookOrgContextProvider.applyContext()`
3. Add a `@Configuration` class explicitly registering the three adapter beans

### Step 3 — Verifier return type alignment

Orkes verifiers return `ErrorList`; OSS `WebhookVerifier` returns `List<String>`. Two options:
- Wrap Orkes verifiers in an adapter shim (`OrkesVerifierAdapter`)
- Update Orkes verifiers to return `List<String>` directly

### Step 4 — Integration test

Run Orkes webhook integration tests against the OSS module at Orkes's constrained dependency versions. This surfaces any remaining binary compat issues before they hit production.

---

## Key File Paths

### OSS
- Module root: `conductor-oss/conductor/webhook-task/`
- Auto-config: `.../webhook/WebhookAutoConfiguration.java`
- Build file: `conductor-oss/conductor/webhook-task/build.gradle`
- Root build: `conductor-oss/conductor/build.gradle`

### Orkes
- Adapter layer: `orkes-conductor/webhooks/src/main/java/io/orkes/conductor/webhook/oss/`
- REST resource (to delete/fix): `orkes-conductor/webhooks/src/main/java/io/orkes/conductor/webhook/rest/IncomingWebhookResource.java`
- Async worker (to fix): `orkes-conductor/webhooks/src/main/java/io/orkes/conductor/WebhookWorker.java`
- Build file: `orkes-conductor/webhooks/build.gradle`
- Root build: `orkes-conductor/build.gradle`

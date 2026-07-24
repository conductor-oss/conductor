# API Contract — Method-not-allowed on scheduler endpoints (Issue #1393)

Reuses names/types from [issue-1393-architecture.md](./issue-1393-architecture.md).
This doc records the HTTP contract that the fix restores and how it interacts with the
Java SDK's probe-then-retry fallback.

## 1. Scheduler endpoint method map

From `scheduler/core/.../rest/SchedulerResource.java` (`@RequestMapping("/api/scheduler")`).
Copied literally from the mapping annotations — the source is the spec.

| Path | Supported method(s) | Handler |
|---|---|---|
| `/schedules` | `POST`, `GET` | `createOrUpdateSchedule`, `getAllSchedules` |
| `/schedules/search` | `GET` | `searchSchedules` |
| `/schedules/{name}` | `GET`, `DELETE` | `getSchedule`, `deleteSchedule` |
| `/schedules/{name}/pause` | **`PUT` only** | `pauseSchedule` |
| `/schedules/{name}/resume` | **`PUT` only** | `resumeSchedule` |
| `/nextFewSchedules` | `GET` | `getNextFewSchedules` |
| `/admin/requeue` | `GET` | `requeueAllExecutionRecords` |
| `/admin/pause` | `GET` | `pauseAllSchedules` |
| `/admin/resume` | `GET` | `resumeAllSchedules` |
| `/search/executions` | `GET` | `searchScheduledExecutions` |

The `pause`/`resume` endpoints are `PUT`-only by design (state mutation). This is **not**
changing.

## 2. The probe request that triggered the bug

The Java SDK probes the pause endpoint with `GET` before falling back to `PUT`:

```
GET /api/scheduler/schedules/{name}/pause
```

Because the path exists but has no `GET` handler, Spring MVC raises
`HttpRequestMethodNotSupportedException`.

### Before the fix (broken)

```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "instance": "<server-id>",
  "status": 500,
  "message": "Request method 'GET' is not supported",
  "retryable": false
}
```

The SDK's `executeGetThenPutOnMethodNotAllowed` only recognizes `405` as the retry signal,
so a `500` is surfaced as `ConductorClientException` and the `PUT` is never attempted.

### After the fix (correct)

```
HTTP/1.1 405 Method Not Allowed
Content-Type: application/json

{
  "instance": "<server-id>",
  "status": 405,
  "message": "Request method 'GET' is not supported",
  "retryable": false
}
```

The SDK sees `405`, falls through, and issues:

```
PUT /api/scheduler/schedules/{name}/pause?reason=<reason>
```

which the server handles normally, returning `200 OK` with an empty body.

## 3. Scope of the contract change

The change is at the framework-error layer (`ApplicationExceptionMapper`), so it applies
uniformly: **any** existing path invoked with an unmapped HTTP method now returns `405`
instead of `500`, across all controllers in the application, not only the scheduler.

No response body schema changes — only the numeric `status` (and the HTTP status line)
moves from `500` to `405`. Clients that already handle `405` (including the Java SDK
fallback) begin working without any client-side change.

## 4. Verification against a live server

The issue's repro exercises the full path. Once the server carries this fix:

```bash
cd java-sdk
export CONDUCTOR_SERVER_URL=http://localhost:8080/api \
       CONDUCTOR_AGENT_LLM_MODEL=anthropic/claude-sonnet-4-6 \
       AGENT_SECONDARY_LLM_MODEL=anthropic/claude-sonnet-4-6
./gradlew :agent-examples:run -PmainClass=org.conductoross.conductor.ai.examples.Example99ScheduledAgent
```

Expected: the lifecycle log proceeds past `pauseSchedule(...)` through resume, preview, and
delete without the `Request method 'GET' is not supported` exception.

<!-- TODO: verify against live server — captured 405/200 responses above are derived from
     the mapping source and Spring MVC semantics, not from a running instance. -->

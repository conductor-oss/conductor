# ES Index Rebuild (Reindex)

When Elasticsearch data is lost but the primary database (Postgres / MySQL) remains intact, this API can be used to re-index all workflows and tasks into ES.

> ⚠️ **This will blow your arm off if you are not careful.** Bulk-writing every workflow and task back to the search cluster can saturate ingest throughput, breach disk watermarks, and cause the cluster to start rejecting requests or enter a red state on any non-trivial deployment.
>
> **Before you call this endpoint:**
> 1. Verify cluster health — `GET /_cluster/health` must report `status: green`;
> 2. Confirm no node is above the disk high-watermark;
> 3. Make sure the live workload can absorb the extra write pressure.
>
> The fact that this is a voluntary on-demand call is the only thing that makes it safe to offer — the operator needs to know what they are doing before calling it. The server performs a pre-flight health check and refuses to start if the cluster is not green. `?force=true` overrides the check, but **do not use this on production without a very good reason**.

## Applicable Scenarios

| Index Backend | Useful? |
|---|---|
| `elasticsearch` / `opensearch2` / `opensearch3` | ✅ Most common scenario — ES is an external service and prone to data loss |
| `postgres` | ⚠️ Limited value — index and data share the same database, typically lost or preserved together |
| Disabled (`indexing.enabled=false`) | ❌ No-op |

---

## API

### Start Reindex

```
POST /api/admin/reindex
POST /api/admin/reindex?force=true   # bypass the cluster-health pre-flight (use with extreme care)
```

**Returns immediately**; the job runs in the background on a single thread. The server runs a `GET /_cluster/health` pre-flight first and refuses to start if the cluster is not `green` and `force=true` was not supplied.

Response example:
```json
{
  "state": "STARTED",
  "message": "Reindex job started. Use GET /api/admin/reindex/status to track progress.",
  "warning": "WARNING: bulk-writing every workflow and task back to the search index can saturate ingest throughput, breach disk watermarks, and drive the cluster into a red state. Verify cluster health (GET /_cluster/health) and available disk space before using this endpoint. This is a voluntary call — the operator is responsible for knowing it is safe to run."
}
```

Pre-flight failure (cluster not green and `force` was not set):
```json
{
  "state": "PREFLIGHT_FAILED",
  "message": "Pre-flight failed: index cluster is not green. Fix the cluster first, or re-run with ?force=true if you understand the risk.",
  "warning": "..."
}
```

If a job is already running:
```json
{
  "state": "ALREADY_RUNNING",
  "message": "A reindex job is already in progress"
}
```

---

### Check Progress

```
GET /api/admin/reindex/status
```

Response example:
```json
{
  "state":     "RUNNING",
  "processed": 350,
  "errors":    0,
  "total":     1240,
  "message":   "Indexing 350 / 1240"
}
```

**`state` values:**

| state | Description |
|---|---|
| `IDLE` | No reindex has been run since the server started |
| `RUNNING` | Job is currently executing in the background |
| `COMPLETED` | All items have been processed |
| `FAILED` | An uncaught exception occurred and the job was aborted |
| `PREFLIGHT_FAILED` | The last call was rejected because the index cluster was not green |
| `ALREADY_RUNNING` | Only appears in POST responses, indicating a duplicate submission was ignored |

Completed response example:
```json
{
  "state":     "COMPLETED",
  "processed": 1240,
  "errors":    2,
  "total":     1240,
  "message":   "Completed. processed=1240, errors=2"
}
```

---

## Procedure

```bash
# 1. Start reindex
curl -X POST http://localhost:8081/api/admin/reindex

# 2. Poll progress (every few seconds)
watch -n 5 'curl -s http://localhost:8081/api/admin/reindex/status | jq .'

# 3. Verify ES index document counts
curl http://localhost:9200/conductor_workflow/_count
curl http://localhost:9200/conductor_task/_count

# 4. Verify search API is working again
curl "http://localhost:8081/api/workflow/search?query=*"
```

---

## Notes

- **Idempotent**: ES uses `workflow_id` as the document ID, so repeated runs overwrite existing documents without creating duplicates.
- **Retriable**: After a `COMPLETED` or `FAILED` state, sending another `POST /reindex` will automatically reset and restart the job.
- **Non-disruptive**: Reindex only reads from the database and writes to ES — it does not modify any business data.
- **Non-zero errors**: Check the conductor-server logs and search for `Failed to reindex workflow` to identify the root cause.

---

## Implementation Details

- Uses a single-thread `ExecutorService` (daemon thread) in the background, without blocking HTTP requests.
- Progress counters use `AtomicInteger` and state uses `AtomicReference<ReindexState>` for thread safety.
- Processes 100 items per batch, logging an INFO message after each batch.
- Related files:
  - `core/.../service/AdminService.java` — interface definition
  - `core/.../service/AdminServiceImpl.java` — async implementation
  - `rest/.../controllers/AdminResource.java` — HTTP endpoints
  - `postgres-persistence/.../dao/PostgresExecutionDAO.java` — `getAllWorkflowIds()` paginated query

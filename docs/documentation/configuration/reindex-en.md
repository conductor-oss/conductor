# ES Index Rebuild (Reindex)

When Elasticsearch data is lost but the primary database (Postgres / MySQL) remains intact, this API can be used to re-index all workflows and tasks into ES.

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
```

**Returns immediately**; the job runs in the background on a single thread.

Response example:
```json
{
  "state": "STARTED",
  "message": "Reindex job started. Use GET /api/admin/reindex/status to track progress."
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

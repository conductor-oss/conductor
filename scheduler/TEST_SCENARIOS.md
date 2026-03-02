# Scheduler Test Scenarios

Running log of all test conditions: completed, in-progress, and planned.
Each entry notes what was learned, any bugs found, and the example files (if any).

---

## Legend

- ✅ Done — verified live against Docker demo stack
- 🐛 Bug found & fixed during live testing
- 📋 Planned — not yet executed
- 🔬 Needs infrastructure / setup before it can run

---

## Single-Instance Functional Tests

These run against a single Conductor server + Postgres (`docker/docker-compose-scheduler-demo.yaml`).

### ✅ 1. Basic scheduled workflow
**Files:** `examples/every-minute-schedule.json`
**What it tests:** Scheduler fires a simple HTTP workflow once per minute. Baseline sanity check — confirms the poll loop, cron parsing, next-run pointer advancement, and execution history all work end-to-end.
**Result:** Passed. Good starting point for first-time setup.

---

### ✅ 2. Catchup mode
**Files:** `examples/catchup-schedule.json`, `examples/catchup-workflow.json`
**What it tests:** `runCatchupScheduleInstances: true` — if the scheduler is offline for N minutes, it should fire once per missed slot on restart, stepping through each slot in sequence (not jumping to now).
**Result:** 🐛 Bug found: `handleSchedule()` used `computeNextRunTime(schedule, now)` even in catchup mode, skipping all missed slots in one jump.
**Fix:** When `runCatchupScheduleInstances=true`, compute next run from `scheduledTime` (current slot), not from `now`. Each poll cycle fires exactly one missed slot.
**Regression test:** `testPollAndExecute_catchupEnabled_firesForEachMissedSlotPerCycle`

---

### ✅ 3. Bounded schedule (scheduleStartTime / scheduleEndTime)
**Files:** `examples/bounded-schedule-template.json`, `examples/bounded-workflow.json`
**What it tests:** Schedule fires only within a defined epoch-ms window. After the last slot before `endTime`, no further executions.
**Result:** 🐛 Bug found: when `computeNextRunTime()` returned null (no future slot within `endTime`), `setNextRunTimeInEpoch` was never called. The pointer stayed at the last slot, causing repeated firing until `now > endTime`.
**Fix:** When `nextRun == null && scheduleEndTime != null`, advance pointer to `endTime + 1`.
**Regression test:** `testHandleSchedule_lastSlotBeforeEndTime_doesNotFireRepeatedly`

---

### ✅ 4. Multi-step FORK/JOIN workflow
**Files:** `examples/multistep-schedule.json`, `examples/multistep-workflow.json`
**What it tests:** Scheduler is agnostic to workflow complexity. A FORK_JOIN workflow with two parallel HTTP calls (timeapi.io UTC + America/New_York) should both complete and produce correct output.
**Result:** Passed after fixing URL encoding. `utcTime` and `newYorkTime` both populated.
**Gotcha:** Use literal `/` in timezone query params (`America/New_York`), not `%2F`. Conductor's HTTP task passes percent-encoded slashes literally — the API rejects it as "Invalid Timezone".

---

### ✅ 5. Failure / retry scenario
**Files:** `examples/retry-schedule.json`, `examples/retry-workflow.json`
**What it tests:** Scheduler keeps firing on its configured interval regardless of prior execution outcome. A workflow that always fails should produce a new FAILED execution record every minute.
**Result:** Passed. 5 consecutive FAILED workflow executions observed, one per minute, each with a fresh EXECUTED entry in the scheduler history.
**Note:** `httpstat.us/500` not reachable from Docker containers. Using Conductor's own API (`/api/workflow/00000000-...`) to get a reliable 404 instead.

---

### ✅ 6. Concurrent execution (stack-up)
**Files:** `examples/concurrent-schedule.json`, `examples/concurrent-workflow.json`
**What it tests:** OSS Conductor scheduler has no concurrent-execution guard. A workflow that takes 90s (WAIT task) fired on a 60s schedule should produce overlapping RUNNING instances.
**Result:** Confirmed. Two workflows simultaneously RUNNING observed (started at :50:06 and :51:06).
**Implication:** Users who want to prevent overlap must implement their own guard (e.g., check for RUNNING instances at workflow start, or use a longer cron interval).
**WAIT task duration format:** Use `"90s"`, `"2m"`, `"1h"` — NOT ISO-8601 `PT90S`. Conductor's `DateTimeUtils.parseDuration` uses its own regex.

---

### ✅ 7. Input parameterization (scheduledTime injection)
**Files:** `examples/input-param-schedule.json`, `examples/input-param-workflow.json`
**What it tests:** Every triggered workflow receives `scheduledTime` (epoch ms, the scheduled slot) and `executionTime` (epoch ms, actual dispatch time) in its input automatically. Static keys from the schedule's `startWorkflowRequest.input` are preserved alongside injected keys.
**Result:** Passed. `scheduledAt=19:45:00.000Z` (on the minute), `triggeredAt=19:45:06.087Z` (actual dispatch), 24h window computed correctly by INLINE JavaScript task.
**Code change:** `SchedulerService.handleSchedule()` now injects `scheduledTime` and `executionTime` into the workflow input before calling `workflowService.startWorkflow()`. Matches Orkes Conductor behavior for convergence.
**Tests added:** `testHandleSchedule_injectsScheduledTimeAndExecutionTimeIntoInput`, `testHandleSchedule_preservesExistingInputKeys`

---

### ✅ 8. DO_WHILE variant (internal looping)
**Files:** `examples/dowhile-schedule.json`, `examples/dowhile-workflow.json`
**What it tests:** A scheduled workflow that internally loops (DO_WHILE) 3 times, fetching the current time on each iteration. Shows the scheduler is agnostic to internal workflow structure.
**Result:** Passed. `iterations=3`, `sampledAt=<last iteration time>`.
**Gotcha:** DO_WHILE task output is keyed by iteration number as a string (`"1"`, `"2"`, `"3"`), not by task reference name at the top level. To reference the last iteration: `${poll_loop.output.3.fetch_current_time.response.body.dateTime}`. Reference task output directly (`${poll_loop.output.fetch_current_time...}`) always resolves null.

---

## Single-Instance Concurrency Tests (Complete)

These run on the same single-instance Docker stack but probe scheduler behavior under concurrent API access.

### ✅ 9. Simultaneous schedule registration (concurrent writes)
**Files:** `scripts/test-09-concurrent-write.sh`
**What it tests:** Two clients (`styx.local` + `spartacus.local`) both call `POST /api/scheduler/schedules` with the same schedule name at the exact same millisecond. Verifies the DB `ON CONFLICT DO UPDATE` UPSERT holds under concurrent writers — no duplicate rows, no constraint violations.
**Setup:** Synchronized bash script using `sleep until <epoch second>` on both machines; both fire curl against `192.168.65.221:8080`.
**Result:** Both got HTTP 201, no errors. Spartacus fired first (createTime T+0ms); styx won UPSERT (createTime T+672ms). Final state consistent — one schedule, no constraint violations.
**Finding:** Concurrent UPSERT is safe. Last writer wins on metadata but `nextRunTime` pointer is consistent. PASS.

---

### ✅ 10. Simultaneous schedule resume → duplicate fire?
**Files:** `scripts/test-10-concurrent-resume.sh`
**What it tests:** A schedule is paused with `nextRunTime` already in the past. Two clients simultaneously call `PUT /api/scheduler/schedules/{name}/resume`. Does the schedule fire twice (one per resume), or does the DB pointer advancement prevent it?
**Setup:** Pause schedule, wait for next slot to pass, then both machines call resume at the same second.
**Result:** Both got HTTP 204. Zero new executions fired. No duplicate fire.
**Finding:** With `runCatchupScheduleInstances=false`, `resumeSchedule()` advances `nextRunTime` to the next **future** slot — the stale slot is never fired immediately. Concurrent resume is safe and idempotent. Not a bug — by design. PASS.

---

### ✅ 11. X jobs at the same moment (thundering herd)
**Files:** `scripts/test-11-thundering-herd.sh`
**What it tests:** Register 50 schedules all with the same cron expression. When the cron fires, all 50 become due simultaneously. Do all 50 workflows start? Are any skipped or duplicated?
**Setup:** Script registers 50 `herd-N` schedules with `0 * * * * *`, waits for the next minute tick, then polls execution history.
**Result:** All 50 schedules fired exactly once. 0 missed, 0 duplicates. PASS.
**Config required:** `poll-batch-size=50` (default is 5 — would require 10 poll cycles at 1s each). `polling-interval=1000` (demo default is 10000ms — 10 cycles at 10s = 100s, far exceeding a 60s minute window).
**Key finding:** With default `poll-batch-size=5` and `polling-interval=10000ms`, only 10 of 50 schedules fire per minute (2 poll cycles × 5 = 10). Production deployments expecting >5 schedules/minute must increase `poll-batch-size`.
**Bugs fixed during testing:** (a) `seq -w` zero-padding created invalid JSON `"herdIndex": 00`; (b) script treated HTTP 201 (create) as error; (c) stale overdue demo schedules consumed batch slots from previous test runs.

---

### ✅ 12. Multi-client simultaneous workflow submission (load)
**Files:** `scripts/test-12-load-blast.py`
**What it tests:** Blast `POST /api/workflow` directly (bypassing the scheduler) at the same instant — simulating what happens at the top of a minute when many scheduled workflows all try to start at once. Measures server response time, error rate, and requests dropped.
**Setup:** Python script using `threading` to fire N concurrent requests. Single-machine (styx) baseline; can be run simultaneously from spartacus for true multi-client blast.
**Result (styx localhost baseline):**
| Concurrency | Wall time | Success | Errors | p95 latency |
|-------------|-----------|---------|--------|-------------|
| 25          | 113ms     | 25/25   | 0      | 110ms       |
| 100         | 266ms     | 100/100 | 0      | 252ms       |
| 200         | 304ms     | 200/200 | 0      | 251ms       |
| 500         | 616ms     | 500/500 | 0      | 427ms       |
| 1000        | 1191ms    | 1000/1000 | 0    | 827ms       |
**Finding:** No saturation point found up to 1000 concurrent requests on localhost. Latency scales roughly linearly. No 429/503 errors observed. Local Docker on Apple Silicon Mac — real saturation point will be lower under network latency + multi-host load. PASS (baseline).

**True multi-client results (styx + spartacus simultaneously):**
| Concurrency (each) | Total | styx p95 | spartacus p95 | Errors |
|--------------------|-------|-----------|---------------|--------|
| 25 + 25            | 50    | 111ms     | 1148ms        | 0      |
| 100 + 100          | 200   | 318ms     | 319ms         | 0      |

**Observation:** At 25+25, spartacus p95 spiked to ~1s (TCP slow-start / first-connection overhead from remote host). At 100+100 the latencies equalized (~320ms both sides). 0 errors across all runs. No saturation found. PASS.

---

## Multi-Instance Scheduler Tests (Needs Infrastructure)

These require two Conductor server instances pointing at the same Postgres DB — to test scheduler behavior in a horizontally-scaled deployment.

### 🔬 13. Two scheduler pollers on the same DB — does a job double-fire?
**What it tests:** The most important HA question: if two Conductor instances both poll at the same millisecond and both see a schedule as due, do they both fire it? The UPSERT in `setNextRunTimeInEpoch` should prevent this, but needs live verification.
**Setup needed:**
- Docker network with two `conductor-server` containers sharing one `conductor-postgres`
- Both servers have `conductor.scheduler.enabled=true`
- Both poll intervals set to 1000ms (default)
**Expected:** Each schedule fires exactly once per slot, regardless of how many server instances are running.
**This is the key HA correctness test for production readiness.**

---

### 🔬 14. Scheduler failover
**What it tests:** One of two Conductor instances dies mid-poll (after writing POLLED state but before writing EXECUTED). Does the surviving instance's stale-POLLED cleanup (`STALE_POLLED_THRESHOLD_MS = 5 min`) correctly transition the record to FAILED? Does the schedule continue firing on the surviving instance?
**Setup needed:** Same as #13, plus ability to kill one container at a controlled point in the poll cycle.

---

### 🔬 15. Uneven poll timing across instances
**What it tests:** Two instances with different polling intervals (e.g., 500ms vs 2000ms). The fast poller fires first; does the slow poller skip the already-fired slot correctly?
**Setup needed:** Same as #13 with different `conductor.scheduler.polling-interval` configs per instance.

---

## Regression / Edge Case Tests (Planned)

### 📋 16. Paused schedule during catchup gap
**What it tests:** Schedule with `runCatchupScheduleInstances: true` is paused for 5 minutes. On resume, it should fire 5 times (once per missed minute) and then settle. Extends the catchup test (#2) with explicit pause/resume.

---

### 📋 17. Schedule with very high firing frequency (sub-second?)
**What it tests:** The minimum cron granularity is 1 second (Spring's 6-field cron). What happens at `"* * * * * *"` (every second)? Does the poll loop keep up? Does the history table grow uncontrollably? Does archival pruning trigger correctly?

---

### 📋 18. Archival pruning under load
**What it tests:** Set `archival-max-records=10`, `archival-max-record-threshold=12`, fire a schedule 20 times. Verify that exactly 10 records are retained and older ones are deleted.
**Status:** Partially covered by unit tests (`SchedulerServicePhase2Test`); needs live verification.

---

### 📋 19. Schedule with DST transition
**What it tests:** A schedule in a DST-aware timezone (e.g., `America/New_York`) across a spring-forward or fall-back transition. Specifically: `0 0 2 * * *` on a night when 2am doesn't exist (spring forward) or occurs twice (fall back). Does Conductor skip it, fire it twice, or handle it gracefully?

---

## Infrastructure Notes

| Machine | Role | Java | Docker | Gradle | Conductor URL |
|---------|------|------|--------|--------|----------------|
| `styx.local` (this machine) | Conductor host + test client | Java 17 (default), 21 at `/opt/homebrew/opt/openjdk@21` | ✅ Docker Desktop | ✅ | `http://localhost:8080` |
| `spartacus.local` | Remote test client | 17 + 21 at `/opt/homebrew/opt/openjdk@21` | ❌ | ❌ | `http://192.168.65.221:8080` |

**Synchronization strategy for multi-client tests:**
Both machines run a shell script that `sleep`s until a specific Unix epoch second (e.g., `sleep $((TARGET_EPOCH - $(date +%s)))`), then fires simultaneously.

---

## Test Counts (as of last update)

| Suite | Tests | Notes |
|-------|-------|-------|
| `PostgresSchedulerDAOTest` | 13 | Testcontainers / real Postgres |
| `SchedulerResourceTest` | 16 | Mockito, pure REST layer |
| `SchedulerServiceTest` | 20 | Unit, no DB |
| `SchedulerServicePhase2Test` | 20 | Edge cases, failure modes, injection |
| `SchedulerServiceStressTest` | 11 | Concurrency, catchup, bounded |
| **Total** | **80** | All passing |

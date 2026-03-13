# Scheduler Stress Test Plan

## Phase 1 (implement now): Timing, Bounds, Catchup

### Category 1 — Timing edge cases

| Test | Method | What it checks |
|------|--------|----------------|
| DST spring-forward gap | `computeNextRunTime_dstSpringForward` | Cron at `0 30 2 * * *` in `America/New_York` on 2024-03-10 (2:30 AM doesn't exist) — next run must skip to 2:30 AM the following day, not produce an invalid time |
| DST fall-back overlap | `computeNextRunTime_dstFallBack` | Cron at `0 30 1 * * *` in `America/New_York` on 2024-11-03 (1:30 AM happens twice) — next run fires exactly once per day |
| Slow poll / overdue | `pollAndExecute_slowPolling_catchesUpOneSlotPerCycle` | nextRunTime 10 minutes in the past (3 missed slots at 3-min intervals) with catchup=false — fires once, advances to next future slot |

**Known issue found:** `computeNextRunTime` does not handle `runCatchupScheduleInstances` at all.
Spring's `CronExpression.next()` handles DST gaps correctly by advancing past non-existent times —
but this needs to be verified explicitly.

---

### Category 2 — Schedule bounds

| Test | Method | What it checks |
|------|--------|----------------|
| Start time in future | `computeNextRunTime_startTimeInFuture` | When `scheduleStartTime` > now, next run is at or after startTime, not before |
| End time in past | `computeNextRunTime_endTimeInPast` | Returns `null` — no future run should be scheduled |
| End time expires mid-run | `isDue_endTimeJustExpired` | `isDue()` returns false when `now > scheduleEndTime` even if nextRunTime was set |
| Start time respected in save | `saveSchedule_startTimeInFuture_nextRunReflectsStartTime` | `saveSchedule` sets nextRunTime ≥ scheduleStartTime |

---

### Category 3 — Catchup behavior

| Test | Method | What it checks |
|------|--------|----------------|
| Resume, catchup disabled | `resumeSchedule_catchupDisabled_nextRunIsInFuture` | After resume, nextRunTime > now (skips missed slots) |
| Resume, catchup enabled | `resumeSchedule_catchupEnabled_nextRunIsInPast` | **BUG**: currently broken — resume always recalculates from now, ignoring the flag. Catchup=true should leave the old nextRunTime so missed slots fire. |
| Poll fires missed slots | `pollAndExecute_catchupEnabled_firesForMissedSlots` | After resume with catchup=true and 3 missed minute-slots, three poll cycles each fire one execution |
| Poll fires only once | `pollAndExecute_catchupDisabled_firesOnce` | After resume with catchup=false, only one execution fires even if 3 slots were missed |

**Fix required in `resumeSchedule`:**
```java
// Current (broken):
Long nextRun = computeNextRunTime(schedule, System.currentTimeMillis());

// Correct:
Long nextRun;
if (schedule.isRunCatchupScheduleInstances()) {
    // Leave the stale nextRunTime in place — poll loop fires for each missed slot
    nextRun = schedulerDAO.getNextRunTimeInEpoch(name);
} else {
    // Skip to the next future slot
    nextRun = computeNextRunTime(schedule, System.currentTimeMillis());
}
```

---

## Phase 2 (after Phase 1 stress tests pass): Failure Modes, Concurrency, Archival, Inputs

### Category 4 — Failure modes

| Test | Method | What it checks |
|------|--------|----------------|
| Workflow not found | `handleSchedule_workflowNotFound_createsFailedRecord` | `workflowService.startWorkflow` throws NotFoundException → execution state = FAILED, reason set |
| Workflow throws generic | `handleSchedule_workflowThrowsRuntimeException_createsFailedRecord` | Any exception → FAILED record, poll loop continues |
| DAO exception in poll | `pollAndExecuteSchedules_daoThrows_logsAndDoesNotCrash` | Exception in `getAllSchedules` is caught, poll loop does not crash the executor |
| Stale POLLED cleanup | `cleanupStalePollRecords_clearsStuckRecords` | **Current impl logs but doesn't act** — verify and fix: records stuck POLLED > 5 min should transition to FAILED |

---

### Category 5 — Concurrency / scale

| Test | Type | What it checks |
|------|------|----------------|
| 50 simultaneous schedules | Integration (Testcontainers) | All 50 due schedules are fired in one poll cycle (within pollBatchSize) |
| Rapid create/delete | Integration | Create and delete same schedule name 20× in rapid succession — no orphaned DB rows |
| Concurrent pause + poll | Unit | Pause a schedule while poll is mid-execution — next poll correctly sees paused=true |

---

### Category 6 — Archival / pruning

| Test | Method | What it checks |
|------|--------|----------------|
| Below threshold, no prune | `pruneExecutionHistory_belowThreshold_doesNotPrune` | With threshold=5, keep=3, and 4 records → no records removed |
| Exceeds threshold, prunes | `pruneExecutionHistory_exceedsThreshold_prunesCorrectly` | With threshold=5, keep=3, and 6 records → 3 most recent kept, 3 oldest removed |
| Prune keeps newest | `pruneExecutionHistory_keepsNewestRecords` | Verify the kept records are the most recent, not arbitrary |

**Bug found in `pruneExecutionHistory`:**
```java
// Current (buggy): fetches threshold+1 but compares against keep
List<...> recent = dao.getExecutionRecords(name, threshold + 1);
if (recent.size() > keep) { ... }   // never true when keep >> threshold

// Correct: trigger on threshold, prune down to keep
if (recent.size() > threshold) {
    for (old : recent.subList(keep, recent.size())) { remove(old); }
}
```

---

### Category 7 — Invalid inputs

| Test | Method | What it checks |
|------|--------|----------------|
| Malformed cron | `saveSchedule_malformedCron_throws400` | `CronExpression.parse` fails → `IllegalArgumentException` with clear message |
| Unknown timezone | `saveSchedule_unknownTimezone_behaviorDocumented` | Currently falls back to UTC silently — decide: reject or accept? Write test that documents the decision |
| Null schedule name | `saveSchedule_nullName_throwsIllegalArgument` | Validate null and blank names |
| End before start | `saveSchedule_endTimeBeforeStartTime_throwsIllegalArgument` | Should reject schedules where endTime ≤ startTime |

**Decision needed:** should an unknown `zoneId` be a validation error (400) or a silent UTC fallback?
Current behavior is silent fallback, which is surprising to users. Recommendation: validate in `saveSchedule`.

---

## Bugs found during planning

| # | Location | Severity | Description |
|---|----------|----------|-------------|
| 1 | `resumeSchedule` | High | `runCatchupScheduleInstances` never checked — catchup is always disabled |
| 2 | `pruneExecutionHistory` | Medium | Compares `size > keep` instead of `size > threshold` — archival never triggers correctly |
| 3 | `cleanupStalePollRecords` | Low | Logs stale POLLED records but never resolves them |
| 4 | `resolveZone` | Low | Invalid timezone silently falls back to UTC rather than failing at save time |

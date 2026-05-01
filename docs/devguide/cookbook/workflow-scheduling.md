---
description: "Conductor cookbook — scheduled workflow recipes for cron-triggered execution, catchup after downtime, bounded time windows, parallel scheduled tasks, input parameterization, and concurrent execution handling."
---

# Scheduled workflow recipes

### Run a workflow every minute

The simplest schedule — trigger a workflow on a fixed interval.

```json
{
  "name": "every-minute-demo-schedule",
  "cronExpression": "0 * * * * *",
  "zoneId": "UTC",
  "startWorkflowRequest": {
    "name": "daily_report_workflow",
    "version": 1,
    "correlationId": "demo-${scheduledTime}"
  },
  "runCatchupScheduleInstances": false,
  "paused": false
}
```

The workflow:

```json
{
  "name": "daily_report_workflow",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "fetch_report_data",
      "taskReferenceName": "fetch_report_data_ref",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://jsonplaceholder.typicode.com/todos?userId=1",
          "method": "GET",
          "connectionTimeOut": 3000,
          "readTimeOut": 3000
        }
      }
    }
  ],
  "outputParameters": {
    "statusCode": "${fetch_report_data_ref.output.response.statusCode}",
    "itemCount": "${fetch_report_data_ref.output.response.body.length()}"
  },
  "timeoutPolicy": "TIME_OUT_WF",
  "timeoutSeconds": 120
}
```

**Register and schedule:**

```shell
# Register workflow
curl -X PUT 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @daily-report-workflow.json

# Create schedule
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d @every-minute-schedule.json

# Watch executions
curl 'http://localhost:8080/api/scheduler/search/executions?freeText=every-minute-demo-schedule&size=10'
```

---

### Weekday business-hours schedule

Trigger a report workflow at 9 AM Eastern on weekdays only.

```json
{
  "name": "daily-report-schedule",
  "cronExpression": "0 0 9 * * MON-FRI",
  "zoneId": "America/New_York",
  "startWorkflowRequest": {
    "name": "daily_report_workflow",
    "version": 1,
    "correlationId": "daily-report-${scheduledTime}"
  },
  "runCatchupScheduleInstances": false,
  "paused": false
}
```

The `zoneId` ensures the schedule respects daylight saving time transitions.

---

### Catch up missed executions after downtime

When the scheduler restarts after being offline, `runCatchupScheduleInstances: true` fires all missed cron slots. Use this for workflows where every execution matters (billing, compliance, ETL).

```json
{
  "name": "catchup-demo-schedule",
  "cronExpression": "0 * * * * *",
  "zoneId": "UTC",
  "runCatchupScheduleInstances": true,
  "paused": false,
  "startWorkflowRequest": {
    "name": "catchup_demo_workflow",
    "version": 1,
    "input": {}
  }
}
```

If the scheduler was down for 5 minutes, it will fire 5 workflow executions on restart — one per missed minute.

!!! warning
    Catchup executions fire in rapid succession. Make sure your workflow and downstream systems can handle the burst.

---

### Bounded schedule with a time window

Restrict a schedule to fire only within a time window using `scheduleStartTime` and `scheduleEndTime` (epoch milliseconds).

```shell
# Compute a 5-minute window starting now
START_MS=$(date +%s000)
END_MS=$(( $(date +%s) + 300 ))000

curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d "{
    \"name\": \"bounded-demo-schedule\",
    \"cronExpression\": \"0 * * * * *\",
    \"zoneId\": \"UTC\",
    \"scheduleStartTime\": $START_MS,
    \"scheduleEndTime\": $END_MS,
    \"startWorkflowRequest\": {
      \"name\": \"bounded_demo_workflow\",
      \"version\": 1,
      \"input\": {}
    }
  }"
```

The schedule fires every minute but only within the 5-minute window, then stops automatically.

---

### Pass input parameters to scheduled workflows

The scheduler automatically injects `_scheduledTime` and `_executedTime` into every execution. You can also provide static input that gets merged:

Schedule definition:

```json
{
  "name": "input-param-demo-schedule",
  "cronExpression": "0 * * * * *",
  "zoneId": "UTC",
  "startWorkflowRequest": {
    "name": "input_param_demo_workflow",
    "version": 1,
    "input": {
      "reportOwner": "platform-team",
      "alertThreshold": 100
    }
  }
}
```

Workflow that uses the injected timestamps to compute a 24-hour report window:

```json
{
  "name": "input_param_demo_workflow",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "compute_report_window",
      "taskReferenceName": "compute_report_window",
      "type": "INLINE",
      "inputParameters": {
        "scheduledTime": "${workflow.input._scheduledTime}",
        "executionTime": "${workflow.input._executedTime}",
        "evaluatorType": "javascript",
        "expression": "function toISO(ms) { return new Date(ms).toISOString(); } ({ reportWindowStart: toISO($.scheduledTime - 86400000), reportWindowEnd: toISO($.scheduledTime), scheduledAt: toISO($.scheduledTime), triggeredAt: toISO($.executionTime) })"
      }
    }
  ],
  "outputParameters": {
    "reportWindowStart": "${compute_report_window.output.result.reportWindowStart}",
    "reportWindowEnd": "${compute_report_window.output.result.reportWindowEnd}",
    "scheduledAt": "${compute_report_window.output.result.scheduledAt}",
    "triggeredAt": "${compute_report_window.output.result.triggeredAt}"
  },
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 30
}
```

---

### Schedule a parallel (FORK/JOIN) workflow

A scheduled workflow can use any Conductor construct. This example fetches two timezones in parallel using FORK_JOIN:

```json
{
  "name": "multistep_demo_workflow",
  "version": 3,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "fork_parallel_calls",
      "taskReferenceName": "fork_parallel_calls",
      "type": "FORK_JOIN",
      "forkTasks": [
        [
          {
            "name": "fetch_utc_time",
            "taskReferenceName": "fetch_utc_time",
            "type": "HTTP",
            "inputParameters": {
              "http_request": {
                "uri": "https://timeapi.io/api/time/current/zone?timeZone=UTC",
                "method": "GET"
              }
            }
          }
        ],
        [
          {
            "name": "fetch_ny_time",
            "taskReferenceName": "fetch_ny_time",
            "type": "HTTP",
            "inputParameters": {
              "http_request": {
                "uri": "https://timeapi.io/api/time/current/zone?timeZone=America/New_York",
                "method": "GET"
              }
            }
          }
        ]
      ]
    },
    {
      "name": "join_results",
      "taskReferenceName": "join_results",
      "type": "JOIN",
      "joinOn": ["fetch_utc_time", "fetch_ny_time"]
    }
  ],
  "outputParameters": {
    "utcTime": "${fetch_utc_time.output.response.body.dateTime}",
    "newYorkTime": "${fetch_ny_time.output.response.body.dateTime}"
  },
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 60
}
```

Schedule it:

```json
{
  "name": "multistep-demo-schedule",
  "cronExpression": "0 * * * * *",
  "zoneId": "UTC",
  "startWorkflowRequest": {
    "name": "multistep_demo_workflow",
    "version": 3
  }
}
```

---

### Handle concurrent executions

The scheduler fires on every cron tick regardless of whether the previous execution has completed. If a workflow takes 90 seconds and the schedule fires every 60 seconds, executions will overlap:

```json
{
  "name": "concurrent_demo_workflow",
  "version": 1,
  "schemaVersion": 2,
  "tasks": [
    {
      "name": "fetch_start_time",
      "taskReferenceName": "fetch_start_time",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://timeapi.io/api/time/current/zone?timeZone=UTC",
          "method": "GET"
        }
      }
    },
    {
      "name": "wait_90s",
      "taskReferenceName": "wait_90s",
      "type": "WAIT",
      "inputParameters": { "duration": "90s" }
    },
    {
      "name": "fetch_end_time",
      "taskReferenceName": "fetch_end_time",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://timeapi.io/api/time/current/zone?timeZone=UTC",
          "method": "GET"
        }
      }
    }
  ],
  "outputParameters": {
    "startedAt": "${fetch_start_time.output.response.body.dateTime}",
    "finishedAt": "${fetch_end_time.output.response.body.dateTime}"
  },
  "timeoutPolicy": "ALERT_ONLY",
  "timeoutSeconds": 300
}
```

!!! note "Design for overlap"
    If concurrent runs are a problem, either increase the cron interval so it exceeds the workflow duration, or make your workflow idempotent so overlapping runs don't produce duplicate side effects.

---

### Manage a schedule lifecycle

Complete lifecycle in one session — create, verify, pause, resume, delete:

```shell
# Create
curl -X POST 'http://localhost:8080/api/scheduler/schedules' \
  -H 'Content-Type: application/json' \
  -d @daily-report-schedule.json

# Preview next 5 execution times
curl 'http://localhost:8080/api/scheduler/nextFewSchedules?cronExpression=0+0+9+*+*+MON-FRI&limit=5'

# Check execution history
curl 'http://localhost:8080/api/scheduler/search/executions?freeText=daily-report-schedule&size=10'

# Pause
curl -X PUT 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule/pause?reason=maintenance'

# Verify paused state
curl 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule'

# Resume
curl -X PUT 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule/resume'

# Delete
curl -X DELETE 'http://localhost:8080/api/scheduler/schedules/daily-report-schedule'
```

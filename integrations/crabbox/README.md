# Crabbox Integration

This directory contains example bridges for running trusted Conductor workloads
through [Crabbox](https://crabbox.sh/). Crabbox leases or delegates to a remote
Linux sandbox, syncs the local checkout, runs a command, streams output back,
and cleans up.

Use this integration when Conductor should orchestrate durable agent, build, or
test steps while Crabbox supplies remote execution capacity. Do not use it as a
hard security boundary for mutually untrusted tenants.

## Quick Start

The fastest smoke path uses Crabbox's `local-container` provider. It requires a
Docker-compatible runtime, but no cloud or Islo credentials.

```bash
crabbox run --provider local-container --shell 'python3 --version'
```

Run the bridge validation through Crabbox:

```bash
crabbox run --provider local-container --shell \
  'python3 -m py_compile integrations/crabbox/*.py && python3 -m json.tool ai/examples/23-crabbox-mcp-run.json >/dev/null && python3 -m json.tool ai/examples/24-crabbox-simple-worker.json >/dev/null && python3 -m json.tool integrations/crabbox/taskdefs/crabbox_run_taskdef.json >/dev/null'
```

The bridge passes `provider` through to Crabbox, so it can use any provider the
installed `crabbox` binary supports. Keep provider-specific credentials and
config in the worker environment or Crabbox config files.

## Trust Boundary

Crabbox is built for trusted operators on a shared team. A task routed through
this bridge can run arbitrary commands on the remote box, and any environment
value allowed through Crabbox configuration may be visible to that box. Keep
provider tokens and broker credentials in the worker environment or Crabbox user
config, never in workflow definitions.

Use Conductor task domains or dedicated worker pools for Crabbox tasks, and use
Crabbox `sync.include`, `sync.exclude`, `env.allow`, TTL, and idle-timeout
settings to keep the remote execution scope narrow. Conductor retries can rerun
the same command on a new sandbox, so only enable retries for idempotent work.

## Integration Modes

### SIMPLE Worker

`conductor_crabbox_worker.py` polls a `SIMPLE` task named `crabbox_run`, executes
the task input with `crabbox run`, streams task logs, sends `IN_PROGRESS`
heartbeats, and returns a terminal Conductor task status.

Register the task definition:

```bash
curl -X POST 'http://localhost:8080/api/metadata/taskdefs' \
  -H 'Content-Type: application/json' \
  -d @integrations/crabbox/taskdefs/crabbox_run_taskdef.json
```

Start the worker:

```bash
export CONDUCTOR_SERVER_URL=http://localhost:8080
python3 integrations/crabbox/conductor_crabbox_worker.py
```

Set `CONDUCTOR_CRABBOX_DOMAIN` when routing these tasks through a dedicated
Conductor task domain.

### MCP Server

`crabbox_mcp_server.py` exposes `crabbox_run_command` and `crabbox_doctor` over
MCP. Conductor workflows can call those tools with `LIST_MCP_TOOLS` and
`CALL_MCP_TOOL`.

```bash
python3 -m pip install -r integrations/crabbox/requirements.txt
python3 integrations/crabbox/crabbox_mcp_server.py --host 127.0.0.1 --port 3001
```

The MCP endpoint is `http://localhost:3001/mcp`.

## Task Input Contract

The `crabbox_run` worker accepts these inputs:

| Field | Type | Default | Description |
|---|---|---|---|
| `command` | string or string array | required | Command to run remotely. Strings run through `--shell` by default. |
| `provider` | string | `local-container` | Any provider supported by the installed `crabbox` binary, for example `local-container`, `islo`, `aws`, `hetzner`, or `ssh`. |
| `workspaceDir` | string | current directory | Local checkout to sync. |
| `profile` | string | none | Crabbox profile name. |
| `leaseId` / `id` | string | none | Existing lease or provider sandbox to reuse. |
| `class` / `machineClass` | string | none | Crabbox machine class. |
| `target` | string | provider default | Crabbox target OS. |
| `ttl` | string | provider default | Maximum lease lifetime. |
| `idleTimeout` | string | provider default | Idle lease timeout. |
| `keep` | boolean | `false` | Keep the lease after success. |
| `keepOnFailure` | boolean | `false` | Keep failed one-shot leases for debugging. |
| `noSync` | boolean | `false` | Skip sync when supported by the provider. |
| `fullResync` | boolean | `false` | Force a fresh sync when supported by the provider. |
| `preflight` | boolean | `false` | Run Crabbox preflight before the command. |
| `timeoutSeconds` | number | none | Local worker timeout for the Crabbox process. |
| `env` | object | `{}` | Extra environment values for the Crabbox CLI process. Prefer Crabbox `env.allow` for remote forwarding. |

The worker returns `status`, `exitCode`, `reason`, `retryable`, `provider`,
`durationSeconds`, `timing`, `output`, `outputTail`, and `truncated`.

## Failure Mapping

The bridge maps Crabbox results into Conductor task statuses:

| Crabbox result | Conductor status |
|---|---|
| Exit code `0` | `COMPLETED` |
| Auth, config, usage, or invalid provider errors | `FAILED_WITH_TERMINAL_ERROR` |
| Capacity, provisioning, sync, timeout, stream, or remote command failures | `FAILED` |

Conductor retries should be reserved for idempotent commands. A task retry may
start a new remote sandbox and rerun the command.

## Islo Proof Runs

`crabbox.islo.yaml` is a narrow config for validating this integration through
Crabbox's Islo delegated provider. It syncs only this integration, the example
workflows, the docs page, and `mkdocs.yml`. Use it when `ISLO_API_KEY` or
`CRABBOX_ISLO_API_KEY` is available.

```bash
tmp_config="$(mktemp)"
install -m 600 integrations/crabbox/crabbox.islo.yaml "$tmp_config"
CRABBOX_CONFIG="$tmp_config" \
  crabbox run --provider islo --shell \
  'python3 -m py_compile integrations/crabbox/*.py && python3 -m json.tool ai/examples/23-crabbox-mcp-run.json >/dev/null && python3 -m json.tool ai/examples/24-crabbox-simple-worker.json >/dev/null'
```

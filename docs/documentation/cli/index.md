---
description: "Install and use the Conductor CLI: manage workflows, tasks, schedules, secrets, webhooks, and a local Conductor server from your terminal."
---

# Conductor CLI

The Conductor CLI (`conductor`) manages Conductor resources — workflows, tasks, schedules, secrets, webhooks — and runs a local Conductor server for development, all from your terminal.

Source and issues: [conductor-oss/conductor-cli](https://github.com/conductor-oss/conductor-cli).

## Installation

### npm

```bash
npm install -g @conductor-oss/conductor-cli
```

This downloads and installs the appropriate binary for your platform.

### macOS / Linux

```bash
curl -fsSL https://raw.githubusercontent.com/conductor-oss/conductor-cli/main/install.sh | sh
```

This detects your OS and architecture, downloads the latest release, and installs to `/usr/local/bin`. To install somewhere else:

```bash
INSTALL_DIR=$HOME/.local/bin curl -fsSL https://raw.githubusercontent.com/conductor-oss/conductor-cli/main/install.sh | sh
```

### Windows

```powershell
irm https://raw.githubusercontent.com/conductor-oss/conductor-cli/main/install.ps1 | iex
```

### Verify

```bash
conductor --version
```

## Commands

```text
Conductor Management:
  api-gateway             API Gateway management commands (Orkes Conductor only)
  schedule                Schedule management
  secret                  Secret management
  task                    Task definition and execution management
  webhook                 Webhook management
  workflow                Workflow definition and execution management

CLI Configuration:
  completion              Generate the autocompletion script for the specified shell
  config                  CLI configuration management
  update                  Update the CLI to the latest version
  whoami                  Display information about the current user

Development:
  code                    Generate projects from templates
  server                  Local Conductor server management
  worker                  Task worker management
```

Run `conductor [command] --help` for the flags and subcommands of any group — for example `conductor workflow --help` or `conductor server --help`.

## Common tasks

Start a local Conductor server:

```bash
conductor server start
```

Register a workflow definition and run it:

```bash
conductor workflow create --file my_workflow.json
conductor workflow start --name my_workflow --input '{}'
```

Keep the CLI current:

```bash
conductor update
```

## Connecting to a server

By default the CLI targets a local OSS server. Point it elsewhere with flags or environment variables:

| Flag | Environment variable | Purpose |
|---|---|---|
| `--server` | `CONDUCTOR_SERVER_URL` | Conductor server URL |
| `--server-type` | `CONDUCTOR_SERVER_TYPE` | `OSS` (default) or `Enterprise` |
| `--auth-key` / `--auth-secret` | `CONDUCTOR_AUTH_KEY` / `CONDUCTOR_AUTH_SECRET` | API credentials |
| `--auth-token` | `CONDUCTOR_AUTH_TOKEN` | Token auth |
| `--profile` | `CONDUCTOR_PROFILE` | Named profile (`config-<profile>.yaml`) |

Profiles are managed with `conductor config`.

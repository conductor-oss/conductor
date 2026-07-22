# Conductor OSS Changelog

## [Unreleased]

### Breaking Changes
- **Scheduler DB migrations folded into the core Flyway chain (one chain per SQL backend)**
  - Scheduler DDL now ships in the core migrations: MySQL `V11`/`V12`, Postgres `V18`/`V19`, SQLite `V6`/`V7`
  - Dedicated scheduler Flyway and its `flyway_schema_history_scheduler` table removed; existing databases upgrade automatically (idempotent no-op apply, orphan history table dropped)
  - `conductor-scheduler-{mysql,postgres,sqlite}-persistence` modules removed — scheduler DAOs now live in the core persistence modules (Java packages unchanged)
  - Scheduler tables are now created on every SQL deployment, even with `conductor.scheduler.enabled=false`
- **`GET /api/scheduler/schedules/{name}` now returns 404 for an unknown schedule**
  - Previously returned `200 OK` with an empty body when the schedule did not exist
  - Now returns `404 Not Found` with the standard error body, matching every other not-found
    response in the API (including this same resource's `pause`/`resume` of a missing schedule)
  - If your integration checks for an empty body to detect a missing schedule, switch to
    handling the `404` status instead
- **JavaScript Evaluator Migration from Nashorn to GraalJS**
  - Minimum Java version is now **Java 17** (previously Java 11+)
  - Nashorn JavaScript engine (deprecated in Java 11, removed in Java 15) replaced with GraalJS
  - ES6+ JavaScript syntax now supported natively
  - All existing JavaScript expressions in workflows will continue to work with improved performance and modern JavaScript features
  - **Note:** This ports the production-tested GraalJS implementation from Orkes Conductor Enterprise

### Added (Ported from Enterprise)
- **GraalJS JavaScript engine** with ES6+ support
  - Based on proven Enterprise implementation
- **Script execution timeout protection** (configurable via `CONDUCTOR_SCRIPT_MAX_EXECUTION_SECONDS`, default: 4 seconds)
  - Prevents infinite loops from hanging workflows
  - Enterprise feature now available in OSS
- **Optional script context pooling** for improved performance (configurable via `CONDUCTOR_SCRIPT_CONTEXT_POOL_ENABLED` and `CONDUCTOR_SCRIPT_CONTEXT_POOL_SIZE`)
  - Disabled by default
  - Enterprise optimization feature
- **ConsoleBridge support** for capturing `console.log()`, `console.info()`, and `console.error()` output from JavaScript tasks
  - Improves observability of JavaScript task execution
  - Enterprise feature now available in OSS
- **Better error messages** with line number information for JavaScript evaluation failures
  - Enhanced debugging capabilities from Enterprise
- **Deep copy protection** to prevent PolyglotMap issues in workflow task data
  - Enterprise stability improvement

### Fixed
- Fresh-database startup no longer order-dependent between the core and scheduler migrations ("Found non-empty schema(s) but no schema history table")
- MySQL server boot with the scheduler enabled no longer fails on an ambiguous `RetryTemplate` bean
- JavaScript evaluation now works on Java 17, 21, and future LTS versions
- Improved security with sandboxed JavaScript execution
- Deep copy protection to prevent PolyglotMap issues in workflow task data

### Configuration
New environment variables for JavaScript evaluation (all optional):
- `CONDUCTOR_SCRIPT_MAX_EXECUTION_SECONDS` - Maximum script execution time (default: 4)
- `CONDUCTOR_SCRIPT_CONTEXT_POOL_SIZE` - Context pool size when enabled (default: 10)
- `CONDUCTOR_SCRIPT_CONTEXT_POOL_ENABLED` - Enable context pooling (default: false)

### Migration Notes
- Update your deployment environment to **Java 17 or higher**
- No changes required to existing workflows - all JavaScript expressions remain compatible
- Modern JavaScript features (const, let, arrow functions, template literals, etc.) are now available
- `CONDUCTOR_NASHORN_ES6_ENABLED` environment variable is no longer needed (ES6+ supported by default)

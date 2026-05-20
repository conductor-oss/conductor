# Conductor OSS Changelog

## [Unreleased]

### Breaking Changes
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

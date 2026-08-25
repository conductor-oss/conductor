# Spring Boot 4 upgrade: issues and decisions

Problems found while executing `SPRING4-Upgrade.md`, the decision taken for each, and any follow-up
work that was deliberately left out of the upgrade.

## 1. spring-retry is no longer managed by the Spring Boot BOM

Spring Boot 4 removed `org.springframework.retry:spring-retry` from its dependency management.
Framework 7 grew its own retry support under `org.springframework.core.retry`, so Boot no longer
ships the standalone library's version.

Conductor uses `RetryTemplate`, `SimpleRetryPolicy` and the backoff policies in 77 places across
core, every SQL persistence module, the scheduler modules, and the ES/OS index DAOs.

Decision: pin `spring-retry` to 2.0.13 in `dependencies.gradle` and version the declarations. The
library only needs `spring-context`, which it declares as optional, so it links against Framework 7
without conflict.

Follow-up: migrating to `org.springframework.core.retry` is a genuine API change. The core API models
retry policies and backoff differently and has no direct equivalent for the `RetryContext` callbacks
Conductor uses in the SQL configurations. Worth doing on its own, not inside this upgrade.

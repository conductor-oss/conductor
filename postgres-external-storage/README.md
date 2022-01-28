# PostgreSQL External Storage Module 

This module use PostgreSQL to store and retrieve workflows/tasks input/output payload that
went over the thresholds defined in properties named `conductor.[workflow|task].[input|output].payload.threshold.kb`.

## Configuration

### Usage

Cf. Documentation [External Payload Storage](https://netflix.github.io/conductor/externalpayloadstorage/#postgresql-storage)

### Example

```properties
conductor.external-payload-storage.type=postgres
conductor.external-payload-storage.postgres.conductor-url=http://localhost:8080
conductor.external-payload-storage.postgres.url=jdbc:postgresql://postgresql:5432/conductor?charset=utf8&parseTime=true&interpolateParams=true
conductor.external-payload-storage.postgres.username=postgres
conductor.external-payload-storage.postgres.password=postgres
conductor.external-payload-storage.postgres.max-data-rows=1000000
conductor.external-payload-storage.postgres.max-data-days=0
conductor.external-payload-storage.postgres.max-data-months=0
conductor.external-payload-storage.postgres.max-data-years=1
```
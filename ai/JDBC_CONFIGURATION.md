# JDBC Configuration

This document describes the configuration format for JDBC database connections in Conductor.

## Overview

Conductor supports configuring **multiple named JDBC instances** for use by the `JDBC` worker task. This allows you to:

- Connect to multiple databases (MySQL, PostgreSQL, Oracle, etc.)
- Separate environments (prod, dev, staging)
- Use different connection pool settings per use case (read-heavy vs write-heavy)

## Configuration Format

JDBC instances are configured using a list-based approach under `conductor.jdbc.instances`:

```yaml
conductor:
  jdbc:
    instances:
      - name: "instance-name"        # Unique identifier for this instance
        connection:                   # Connection configuration
          datasourceURL: "jdbc:..."   # JDBC connection URL
          jdbcDriver: "..."           # JDBC driver class (optional, auto-detected from URL)
          user: "..."                 # Database username
          password: "..."             # Database password
          # ... pool settings
```

## Configuration Examples

### Single MySQL Instance

```yaml
conductor:
  jdbc:
    instances:
      - name: "mysql-prod"
        connection:
          datasourceURL: "jdbc:mysql://prod-db:3306/myapp"
          jdbcDriver: "com.mysql.cj.jdbc.Driver"
          user: "conductor"
          password: "secret"
          maximumPoolSize: 20
          minimumIdle: 5
```

### Multiple Instances

```yaml
conductor:
  jdbc:
    instances:
      - name: "mysql-prod"
        connection:
          datasourceURL: "jdbc:mysql://prod-db:3306/myapp"
          jdbcDriver: "com.mysql.cj.jdbc.Driver"
          user: "conductor"
          password: "prod-secret"
          maximumPoolSize: 20

      - name: "postgres-analytics"
        connection:
          datasourceURL: "jdbc:postgresql://analytics-db:5432/warehouse"
          user: "analyst"
          password: "analytics-secret"
          maximumPoolSize: 10

      - name: "mysql-staging"
        connection:
          datasourceURL: "jdbc:mysql://staging-db:3306/myapp"
          jdbcDriver: "com.mysql.cj.jdbc.Driver"
          user: "conductor"
          password: "staging-secret"
          maximumPoolSize: 5
          minimumIdle: 1
```

## Usage in Workflows

When using the JDBC task in your workflows, reference the instance by its configured name using `connectionId`:

```json
{
  "name": "query_users",
  "taskReferenceName": "query_users_ref",
  "type": "JDBC",
  "inputParameters": {
    "connectionId": "mysql-prod",
    "type": "SELECT",
    "statement": "SELECT id, name, email FROM users WHERE status = ?",
    "parameters": ["active"]
  }
}
```

### SELECT Example

```json
{
  "name": "find_orders",
  "taskReferenceName": "find_orders_ref",
  "type": "JDBC",
  "inputParameters": {
    "connectionId": "postgres-analytics",
    "type": "SELECT",
    "statement": "SELECT order_id, total FROM orders WHERE customer_id = ?",
    "parameters": ["${workflow.input.customerId}"]
  }
}
```

Output:
```json
{
  "result": [
    {"order_id": 101, "total": 49.99},
    {"order_id": 205, "total": 129.50}
  ]
}
```

### UPDATE Example

```json
{
  "name": "update_status",
  "taskReferenceName": "update_status_ref",
  "type": "JDBC",
  "inputParameters": {
    "connectionId": "mysql-prod",
    "type": "UPDATE",
    "statement": "UPDATE orders SET status = ? WHERE order_id = ?",
    "parameters": ["shipped", "${workflow.input.orderId}"],
    "expectedUpdateCount": 1
  }
}
```

Output:
```json
{
  "update_count": 1
}
```

If the actual update count does not match `expectedUpdateCount`, the transaction is rolled back and the task fails.

## Connection Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `datasourceURL` | String | Required | JDBC connection URL |
| `jdbcDriver` | String | Auto-detected | JDBC driver class name |
| `user` | String | Optional | Database username |
| `password` | String | Optional | Database password |
| `maximumPoolSize` | Integer | 32 | Maximum connections in the pool |
| `minimumIdle` | Integer | 2 | Minimum idle connections |
| `idleTimeoutMs` | Long | 30000 | Idle connection timeout (ms) |
| `connectionTimeout` | Long | 30000 | Connection acquisition timeout (ms) |
| `leakDetectionThreshold` | Long | 60000 | Leak detection threshold (ms) |
| `maxLifetime` | Long | 1800000 | Maximum connection lifetime (ms) |

## Migration from Old Configuration

### Old Format

```properties
conductor.worker.jdbc.connectionIds=mysql,postgres
conductor.worker.jdbc.mysql.connectionURL=jdbc:mysql://localhost:3306/db
conductor.worker.jdbc.mysql.driverClassName=com.mysql.cj.jdbc.Driver
conductor.worker.jdbc.mysql.username=root
conductor.worker.jdbc.mysql.password=secret
conductor.worker.jdbc.mysql.maximum-pool-size=10

conductor.worker.jdbc.postgres.connectionURL=jdbc:postgresql://localhost:5432/db
conductor.worker.jdbc.postgres.driverClassName=org.postgresql.Driver
conductor.worker.jdbc.postgres.username=pguser
conductor.worker.jdbc.postgres.password=pgpass
```

### New Format

```yaml
conductor:
  jdbc:
    instances:
      - name: "mysql"
        connection:
          datasourceURL: "jdbc:mysql://localhost:3306/db"
          jdbcDriver: "com.mysql.cj.jdbc.Driver"
          user: "root"
          password: "secret"
          maximumPoolSize: 10

      - name: "postgres"
        connection:
          datasourceURL: "jdbc:postgresql://localhost:5432/db"
          jdbcDriver: "org.postgresql.Driver"
          user: "pguser"
          password: "pgpass"
```

**Note:** The old `conductor.worker.jdbc.*` format is still supported for backwards compatibility. If no `conductor.jdbc.instances` are configured, the system automatically falls back to reading the legacy format. The old and new formats are mutually exclusive -- if new-format instances are found, the legacy format is ignored.

### Property Name Mapping

| Old Property | New Property |
|---|---|
| `connectionURL` | `datasourceURL` |
| `driverClassName` | `jdbcDriver` |
| `username` | `user` |
| `password` | `password` |
| `maximum-pool-size` | `maximumPoolSize` |
| `idle-timeout-ms` | `idleTimeoutMs` |
| `minimum-idle` | `minimumIdle` |

## Best Practices

1. **Use descriptive names**: Choose instance names that clearly indicate their purpose (e.g., `mysql-prod`, `postgres-analytics`, `oracle-reporting`)

2. **Separate read/write pools**: For high-throughput systems, configure separate instances for read and write operations with appropriate pool sizes

3. **Right-size connection pools**: Set `maximumPoolSize` based on your database capacity and workload. A common formula is `connections = (core_count * 2) + effective_spindle_count`

4. **Enable leak detection**: The default `leakDetectionThreshold` of 60 seconds logs warnings for connections held longer than expected

5. **Use parameterized queries**: Always use `?` placeholders with the `parameters` list instead of string concatenation to prevent SQL injection

6. **Set expectedUpdateCount**: For critical UPDATE/INSERT/DELETE operations, set `expectedUpdateCount` to automatically rollback if the affected row count doesn't match

## Troubleshooting

### Instance Not Found

If you see "JDBC instance not found: xyz", check:

1. The `connectionId` in your workflow matches the configured `name` exactly
2. The instance is properly configured in your application.yml/properties
3. The application has been restarted after configuration changes

### Connection Timeout

If connections are timing out:

1. Verify network connectivity to the database
2. Check `connectionTimeout` value (default 30 seconds)
3. Ensure the connection pool is not exhausted (increase `maximumPoolSize` if needed)
4. Check database max connections limit

### Connection Leaks

If you see leak detection warnings:

1. Ensure all connections are properly closed (the JDBC worker handles this automatically)
2. If using custom integrations, wrap connection usage in try-with-resources
3. Review `leakDetectionThreshold` setting

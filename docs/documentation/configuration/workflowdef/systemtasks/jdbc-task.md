---
description: "Configure JDBC tasks in Conductor to execute SQL queries and updates against relational databases. Supports SELECT, UPDATE, and parameterized queries with connection pooling."
---

# JDBC Task

```json
"type" : "JDBC"
```

The JDBC task (`JDBC`) executes SQL statements against relational databases. It supports SELECT queries, UPDATE/INSERT/DELETE statements, parameterized queries, and transaction management with automatic rollback on failure.

Multiple named database connections can be configured, allowing workflows to interact with different databases (MySQL, PostgreSQL, Oracle, etc.) within the same workflow.

## Task parameters

| Parameter          | Type         | Description                                       | Required / Optional  |
| ------------------ | ------------ | ------------------------------------------------- | -------------------- |
| connectionId       | String       | The name of the configured JDBC instance to use. Must match a name from `conductor.jdbc.instances` configuration. | Required (unless `integrationName` is used). |
| integrationName    | String       | The name of a managed integration (multi-tenant). Used instead of `connectionId` for platform-managed connections. | Optional. |
| type               | String       | The SQL operation type. Supported: `SELECT`, `UPDATE`. | Required. |
| statement          | String       | The SQL statement to execute. Use `?` for parameterized queries. | Required. |
| parameters         | List[String] | Ordered list of parameter values for `?` placeholders in the statement. | Optional. |
| expectedUpdateCount | Integer     | For `UPDATE` type only. If specified, the transaction is rolled back when the actual update count doesn't match. | Optional. |
| schemaName         | String       | Database schema name (reserved for future use). | Optional. |

## Configuration JSON

### SELECT query

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

### UPDATE with expected count

```json
{
  "name": "update_order_status",
  "taskReferenceName": "update_order_ref",
  "type": "JDBC",
  "inputParameters": {
    "connectionId": "mysql-prod",
    "type": "UPDATE",
    "statement": "UPDATE orders SET status = ? WHERE order_id = ?",
    "parameters": [
      "shipped",
      "${workflow.input.orderId}"
    ],
    "expectedUpdateCount": 1
  }
}
```

## Output

### SELECT output

| Name   | Type | Description |
| ------ | ---- | ----------- |
| result | List[Map[String, Any]] | List of rows, where each row is a map of column names to values. |

Example output:

```json
{
  "result": [
    {"id": 1, "name": "Alice", "email": "alice@example.com"},
    {"id": 2, "name": "Bob", "email": "bob@example.com"}
  ]
}
```

### UPDATE output

| Name   | Type | Description |
| ------ | ---- | ----------- |
| update_count | Integer | The number of rows affected by the statement. |

Example output:

```json
{
  "update_count": 1
}
```

## Transaction behavior

- **SELECT** statements run with auto-commit enabled (default JDBC behavior).
- **UPDATE** statements run with auto-commit disabled. The transaction is committed on success.
- If `expectedUpdateCount` is set and the actual count doesn't match, the transaction is **automatically rolled back** and the task fails.
- If a SQL exception occurs during an UPDATE, the transaction is **automatically rolled back**.

## Connection configuration

JDBC connections are configured using named instances under `conductor.jdbc.instances`. For the full configuration reference, see [JDBC Configuration](../../../../../ai/JDBC_CONFIGURATION.md).

### Quick setup

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

      - name: "postgres-analytics"
        connection:
          datasourceURL: "jdbc:postgresql://analytics-db:5432/warehouse"
          user: "analyst"
          password: "secret"
```

### Connection pool options

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

## Execution

The JDBC task completes as follows:

- **COMPLETED**: The SQL statement executed successfully. For SELECT, results are in `output.result`. For UPDATE, the count is in `output.update_count`.
- **FAILED**: The task fails if:
    - The `connectionId` doesn't match any configured instance.
    - A SQL exception occurs (syntax error, constraint violation, connection timeout).
    - The `expectedUpdateCount` doesn't match the actual update count (UPDATE only, triggers rollback).

## Examples

### Parameterized SELECT

```json
{
  "name": "find_active_orders",
  "taskReferenceName": "find_orders_ref",
  "type": "JDBC",
  "inputParameters": {
    "connectionId": "postgres-analytics",
    "type": "SELECT",
    "statement": "SELECT order_id, total, created_at FROM orders WHERE customer_id = ? AND status = ? ORDER BY created_at DESC",
    "parameters": [
      "${workflow.input.customerId}",
      "active"
    ]
  }
}
```

### INSERT with expected count

```json
{
  "name": "create_audit_record",
  "taskReferenceName": "audit_ref",
  "type": "JDBC",
  "inputParameters": {
    "connectionId": "mysql-prod",
    "type": "UPDATE",
    "statement": "INSERT INTO audit_log (action, user_id, details, created_at) VALUES (?, ?, ?, NOW())",
    "parameters": [
      "${workflow.input.action}",
      "${workflow.input.userId}",
      "${workflow.input.details}"
    ],
    "expectedUpdateCount": 1
  }
}
```

### Chaining SELECT and UPDATE

Use the output of a SELECT task as input to an UPDATE task:

```json
[
  {
    "name": "get_order",
    "taskReferenceName": "get_order_ref",
    "type": "JDBC",
    "inputParameters": {
      "connectionId": "mysql-prod",
      "type": "SELECT",
      "statement": "SELECT id, total FROM orders WHERE order_id = ?",
      "parameters": ["${workflow.input.orderId}"]
    }
  },
  {
    "name": "apply_discount",
    "taskReferenceName": "apply_discount_ref",
    "type": "JDBC",
    "inputParameters": {
      "connectionId": "mysql-prod",
      "type": "UPDATE",
      "statement": "UPDATE orders SET total = total * 0.9 WHERE order_id = ? AND total > 0",
      "parameters": ["${workflow.input.orderId}"],
      "expectedUpdateCount": 1
    }
  }
]
```

### Using with different databases in the same workflow

```json
[
  {
    "name": "read_from_mysql",
    "taskReferenceName": "mysql_read_ref",
    "type": "JDBC",
    "inputParameters": {
      "connectionId": "mysql-prod",
      "type": "SELECT",
      "statement": "SELECT user_id, email FROM users WHERE user_id = ?",
      "parameters": ["${workflow.input.userId}"]
    }
  },
  {
    "name": "write_to_postgres",
    "taskReferenceName": "pg_write_ref",
    "type": "JDBC",
    "inputParameters": {
      "connectionId": "postgres-analytics",
      "type": "UPDATE",
      "statement": "INSERT INTO user_activity (user_id, email, event_type, event_time) VALUES (?, ?, ?, NOW())",
      "parameters": [
        "${workflow.input.userId}",
        "${mysql_read_ref.output.result[0].email}",
        "workflow_triggered"
      ],
      "expectedUpdateCount": 1
    }
  }
]
```

!!! warning "SQL injection"
    Always use parameterized queries (`?` placeholders with the `parameters` list). Never concatenate user input directly into SQL statements.

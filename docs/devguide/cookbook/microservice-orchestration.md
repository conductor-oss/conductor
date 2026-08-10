---
description: "Conductor cookbook — microservice orchestration recipes with HTTP service chains, conditional branching, and parallel HTTP calls using Fork/Join."
---

# Microservice orchestration

### HTTP service chain

A common pattern: call a series of HTTP endpoints where each step uses output from the previous one. No custom workers needed — Conductor handles it with built-in HTTP tasks.

```json
{
  "name": "order_processing",
  "description": "Validate order, charge payment, reserve inventory, send confirmation",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["orderId", "customerId", "amount", "items"],
  "tasks": [
    {
      "name": "validate_order",
      "taskReferenceName": "validate",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://api.example.com/orders/${workflow.input.orderId}/validate",
          "method": "POST",
          "body": {
            "customerId": "${workflow.input.customerId}",
            "items": "${workflow.input.items}"
          },
          "connectionTimeOut": 5000,
          "readTimeOut": 5000
        }
      }
    },
    {
      "name": "charge_payment",
      "taskReferenceName": "payment",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://api.example.com/payments/charge",
          "method": "POST",
          "body": {
            "orderId": "${workflow.input.orderId}",
            "amount": "${workflow.input.amount}",
            "customerId": "${workflow.input.customerId}"
          },
          "connectionTimeOut": 10000,
          "readTimeOut": 10000
        }
      }
    },
    {
      "name": "reserve_inventory",
      "taskReferenceName": "inventory",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://api.example.com/inventory/reserve",
          "method": "POST",
          "body": {
            "orderId": "${workflow.input.orderId}",
            "items": "${workflow.input.items}",
            "paymentId": "${payment.output.response.body.paymentId}"
          },
          "connectionTimeOut": 5000,
          "readTimeOut": 5000
        }
      }
    },
    {
      "name": "send_confirmation",
      "taskReferenceName": "notify",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://api.example.com/notifications/send",
          "method": "POST",
          "body": {
            "customerId": "${workflow.input.customerId}",
            "orderId": "${workflow.input.orderId}",
            "paymentId": "${payment.output.response.body.paymentId}",
            "reservationId": "${inventory.output.response.body.reservationId}"
          }
        }
      }
    }
  ],
  "outputParameters": {
    "paymentId": "${payment.output.response.body.paymentId}",
    "reservationId": "${inventory.output.response.body.reservationId}"
  },
  "failureWorkflow": "order_compensation",
  "timeoutPolicy": "TIME_OUT_WF",
  "timeoutSeconds": 120
}
```

Each task passes data forward using `${taskReferenceName.output.response.body.field}` expressions. If any step fails, Conductor retries it (configurable) and can trigger the `failureWorkflow` for compensation.

**Register and run:**

```shell
curl -X POST 'http://localhost:8080/api/metadata/workflow' \
  -H 'Content-Type: application/json' \
  -d @order_processing.json

curl -X POST 'http://localhost:8080/api/workflow/order_processing' \
  -H 'Content-Type: application/json' \
  -d '{"orderId": "ORD-123", "customerId": "CUST-456", "amount": 99.99, "items": ["SKU-A", "SKU-B"]}'
```

---

### HTTP with conditional branching

Use a SWITCH operator to route workflow execution based on a previous task's output.

```json
{
  "name": "user_onboarding",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["userId"],
  "tasks": [
    {
      "name": "get_user_profile",
      "taskReferenceName": "profile",
      "type": "HTTP",
      "inputParameters": {
        "http_request": {
          "uri": "https://api.example.com/users/${workflow.input.userId}",
          "method": "GET"
        }
      }
    },
    {
      "name": "route_by_tier",
      "taskReferenceName": "tier_switch",
      "type": "SWITCH",
      "evaluatorType": "javascript",
      "expression": "$.tier == 'enterprise' ? 'enterprise' : 'standard'",
      "inputParameters": {
        "tier": "${profile.output.response.body.tier}"
      },
      "decisionCases": {
        "enterprise": [
          {
            "name": "assign_account_manager",
            "taskReferenceName": "assign_am",
            "type": "HTTP",
            "inputParameters": {
              "http_request": {
                "uri": "https://api.example.com/account-managers/assign",
                "method": "POST",
                "body": {"userId": "${workflow.input.userId}"}
              }
            }
          }
        ],
        "standard": [
          {
            "name": "send_welcome_email",
            "taskReferenceName": "welcome",
            "type": "HTTP",
            "inputParameters": {
              "http_request": {
                "uri": "https://api.example.com/emails/welcome",
                "method": "POST",
                "body": {"userId": "${workflow.input.userId}"}
              }
            }
          }
        ]
      }
    }
  ]
}
```

---

### Parallel HTTP calls with Fork/Join

When tasks are independent, run them in parallel with a static fork.

```json
{
  "name": "enrich_customer_data",
  "version": 1,
  "schemaVersion": 2,
  "inputParameters": ["customerId"],
  "tasks": [
    {
      "name": "parallel_enrichment",
      "taskReferenceName": "fork",
      "type": "FORK_JOIN",
      "forkTasks": [
        [
          {
            "name": "get_credit_score",
            "taskReferenceName": "credit",
            "type": "HTTP",
            "inputParameters": {
              "http_request": {
                "uri": "https://api.example.com/credit/${workflow.input.customerId}",
                "method": "GET"
              }
            }
          }
        ],
        [
          {
            "name": "get_purchase_history",
            "taskReferenceName": "purchases",
            "type": "HTTP",
            "inputParameters": {
              "http_request": {
                "uri": "https://api.example.com/purchases/${workflow.input.customerId}",
                "method": "GET"
              }
            }
          }
        ],
        [
          {
            "name": "get_support_tickets",
            "taskReferenceName": "tickets",
            "type": "HTTP",
            "inputParameters": {
              "http_request": {
                "uri": "https://api.example.com/support/${workflow.input.customerId}",
                "method": "GET"
              }
            }
          }
        ]
      ]
    },
    {
      "name": "join_results",
      "taskReferenceName": "join",
      "type": "JOIN",
      "joinOn": ["credit", "purchases", "tickets"]
    }
  ],
  "outputParameters": {
    "creditScore": "${credit.output.response.body}",
    "purchases": "${purchases.output.response.body}",
    "tickets": "${tickets.output.response.body}"
  }
}
```

All three HTTP calls execute simultaneously. The JOIN waits for all to complete before the workflow continues.

# Conductor Schemas

This directory contains JSON Schema definitions for the core data models used in Conductor workflow orchestration.

## Overview

JSON Schemas provide a standardized way to describe the structure, validation rules, and documentation for Conductor's data models. These schemas can be used for:

- **Validation**: Validate workflow and task definitions before submitting them to Conductor
- **Documentation**: Auto-generate API documentation and client libraries
- **IDE Support**: Enable autocomplete and validation in editors that support JSON Schema
- **Code Generation**: Generate strongly-typed client code in various programming languages
- **Contract Testing**: Ensure API responses conform to expected formats

## Schema Files

### WorkflowDef.json

**Purpose**: Defines the structure of a workflow definition (template/blueprint).

**Key Features**:
- Workflow metadata (name, version, description, owner)
- List of tasks that comprise the workflow
- Input/output parameters and templates
- Timeout and retry policies
- Rate limiting and caching configurations
- Schema enforcement for input/output validation

**Important Details**:
- Contains a recursive `WorkflowTask` definition that supports complex workflow patterns:
  - **Decision tasks**: Branch based on conditions (`decisionCases`, `defaultCase`)
  - **Fork-Join tasks**: Execute tasks in parallel (`forkTasks`)
  - **Do-While tasks**: Loop over tasks (`loopOver`, `loopCondition`)
  - **Sub-workflow tasks**: Embed entire workflows (`subWorkflowParam`)
- The recursive nature allows unlimited nesting depth for complex workflow patterns

**Use Case**: Use this schema when creating or validating workflow definitions before registering them with Conductor.

---

### TaskDef.json

**Purpose**: Defines the structure of a task definition (reusable task template).

**Key Features**:
- Task metadata (name, description, owner)
- Retry configuration (count, delay, logic type)
- Timeout policies and durations
- Rate limiting settings
- Input/output schema validation
- Concurrency controls

**Important Details**:
- Task definitions are registered separately and can be reused across multiple workflows
- Supports three retry strategies: FIXED, EXPONENTIAL_BACKOFF, LINEAR_BACKOFF
- Timeout policies determine workflow behavior: RETRY, TIME_OUT_WF, ALERT_ONLY

**Use Case**: Use this schema when creating or validating task definitions that will be referenced by workflows.

---

### Workflow.json

**Purpose**: Represents a runtime workflow instance (actual execution).

**Key Features**:
- Current execution status (RUNNING, COMPLETED, FAILED, etc.)
- List of task instances that have been scheduled or executed
- Input/output data for the workflow execution
- Timing information (start, end, update times)
- Parent/child workflow relationships
- Workflow variables and correlation IDs

**Important Details**:
- Contains a **recursive `history` field** that stores previous executions for workflow versioning and auditing
- References the `WorkflowDef` that defines the workflow structure
- Each workflow has a unique `workflowId`
- Priority ranges from 0-99 (higher = more priority)
- External payload storage paths for large inputs/outputs

**Use Case**: Use this schema when querying workflow execution status or validating workflow runtime data.

---

### Task.json

**Purpose**: Represents a runtime task instance (actual task execution).

**Key Features**:
- Current task status with detailed state information
- Input/output data for the task execution
- Worker information (workerId, domain)
- Retry and poll counts
- Timing data (scheduled, start, end, update times)
- Sub-workflow references for SUB_WORKFLOW tasks

**Important Details**:
- Task status enum includes:
  - **Running states**: IN_PROGRESS, SCHEDULED
  - **Success states**: COMPLETED, COMPLETED_WITH_ERRORS, SKIPPED
  - **Failure states**: FAILED, FAILED_WITH_TERMINAL_ERROR, TIMED_OUT, CANCELED
- Contains reference to the `WorkflowTask` template definition
- For loop tasks: `iteration` field tracks the current iteration number
- `executionMetadata` provides detailed timing and worker context information

**Use Case**: Use this schema when querying individual task execution details or validating task runtime data.

---

## Common Patterns

### Inheritance Hierarchy

All definition schemas (WorkflowDef, TaskDef, SchemaDef) inherit audit fields from the `Auditable` base class:
- `ownerApp`: Application that owns this definition
- `createTime`: Timestamp when created (milliseconds since epoch)
- `updateTime`: Timestamp when last updated
- `createdBy`: User who created the definition
- `updatedBy`: User who last updated the definition

Additionally, definitions implement the `Metadata` interface requiring:
- `name`: Unique identifier
- `version`: Version number

### Recursive Structures

The schemas correctly model two important recursive relationships in Conductor:

**WorkflowTask recursion**: Tasks can contain nested tasks for control flow
   ```
   WorkflowTask
   ├── decisionCases: Map<String, List<WorkflowTask>>
   ├── defaultCase: List<WorkflowTask>
   ├── forkTasks: List<List<WorkflowTask>>
   └── loopOver: List<WorkflowTask>
   ```
### Schema Validation

Schemas are defined to support JSON Schema validation using the `$ref` keyword. Both internal references (`#/definitions/...`) and external references are supported.

### Enumerations

All enum types from the Java code are represented as string enums with allowed values explicitly listed:
- Workflow status: `RUNNING`, `COMPLETED`, `FAILED`, `TIMED_OUT`, `TERMINATED`, `PAUSED`
- Task status: `IN_PROGRESS`, `CANCELED`, `FAILED`, `FAILED_WITH_TERMINAL_ERROR`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, `SCHEDULED`, `TIMED_OUT`, `SKIPPED`
- Timeout policies: `RETRY`, `TIME_OUT_WF`, `ALERT_ONLY`
- Retry logic: `FIXED`, `EXPONENTIAL_BACKOFF`, `LINEAR_BACKOFF`
- Schema types: `JSON`, `AVRO`, `PROTOBUF`

## Usage Examples

### Validating a Workflow Definition

```bash
# Using ajv-cli
ajv validate -s schemas/WorkflowDef.json -d my-workflow.json

# Using Python with jsonschema
python -c "
import json
import jsonschema

with open('schemas/WorkflowDef.json') as f:
    schema = json.load(f)

with open('my-workflow.json') as f:
    workflow = json.load(f)

jsonschema.validate(workflow, schema)
print('Valid workflow definition!')
"
```

### IDE Integration

Most modern IDEs support JSON Schema for validation and autocomplete:

**VS Code**: Add this to the top of your JSON file:
```json
{
  "$schema": "./schemas/WorkflowDef.json",
  "name": "my-workflow",
  ...
}
```

**IntelliJ IDEA**: Configure JSON Schema mappings in Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings

## Schema Specifications

All schemas conform to **JSON Schema Draft 07** specification (`http://json-schema.org/draft-07/schema#`).

Key validation features used:
- `type`: Data type constraints
- `required`: Required fields
- `minimum`/`maximum`: Numeric bounds
- `minLength`/`maxLength`: String length constraints
- `minItems`: Array minimum length
- `pattern`: Regular expression matching
- `enum`: Allowed values
- `format`: Data format hints (email, int64, etc.)
- `default`: Default values
- `additionalProperties`: Allow/disallow extra fields
- `$ref`: Schema composition and reuse

## Relationship to Java Models

These schemas are derived from the Java model classes in the Conductor codebase:

| Schema File | Java Class | Package |
|-------------|------------|---------|
| WorkflowDef.json | `WorkflowDef` | `com.netflix.conductor.common.metadata.workflow` |
| TaskDef.json | `TaskDef` | `com.netflix.conductor.common.metadata.tasks` |
| Workflow.json | `Workflow` | `com.netflix.conductor.common.run` |
| Task.json | `Task` | `com.netflix.conductor.common.metadata.tasks` |

The schemas accurately reflect:
- All fields including inherited fields from `Auditable` and `Metadata`
- Jackson annotations for serialization behavior
- Jakarta validation constraints
- Protobuf field mappings

## Version

Current schema version: 1.0
Based on Conductor version: 3.x
Last updated: October 2025

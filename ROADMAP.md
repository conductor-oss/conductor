# Conductor OSS Roadmap


## New Features
### Type safety for workflow inputs and task input/output through JSON Schema

* Allow type safe workflows and workers with support for JSON schema and protobuf
* Enable scaffolding code generation for workers through schema for workers using CLI tool

### New System Tasks

* Database task to work with relational & no-sql databases
* Polling support for HTTP task
* Operators
 * * For..Each with parallel and sequential execution
 * * Improved While loop
 * *  Try..Catch for improved error handling at the task level

### LLM Integrations
Conductor is a perfect platform to build your next LLM powered application or incorporating genAI into your applications.
Enable system tasks for LLM integrations that lets you work with various language models for:
1. Text completion
2. Chat completion with memory
3. Embedding generation

### CLI for Conductor
Allow developers to manage their conductor instance via CLI.

* Manage metadata
* Query and manage workflow executions (terminate, pause, resume, retry)
* Start | Stop manage conductor server

### Support Python as a scripting language for INLINE task
Extend usability of Conductor by allowing lightweight python code as INLINE tasks.

### New APIs for workflow state management

* Synchronous execution of workflows
* update workflow variables
* Update tasks synchronously

## SDKs

* Rust
* Kotlin
* C++
* Ruby
* Swift
* Flutter / Dart 
* PHP

### Worker metrics on server
Expose an endpoint on the server that can be used by workers to publish worker specific metrics.
This will allow monitoring metrics for all the workers in a distributed system across the entire system. 

## Testing
Infrastructure to make workflows easier to test and debug right from the UI and IDE.

### Workflow Debugger

* Ability to debug your workflows during development just like you would do when you write code
* All functionality of a debugger
* Breakpoints add/remove
* Step to next
* Drop to a certain task that was already executed. (going back in time)
* Ability to inspect, modify, add input / output parameters
* Watch Windows to see values of interesting &nbsp;parameters during execution
* Attaching to a certain WF execution
* Remote Task debugging (with SDK Support).. Enable step by step execution in a task worker from the server

## Maintenance

1. Deprecate support for Elasticsearch 6
2. Update support for newer versions of Elasticsearch
2. Improve/Fix JOIN task performance (less about making it performant and more about just fixing the usability) &nbsp;- Done
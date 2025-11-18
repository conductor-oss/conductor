# Introducing Conductor

Conductor enables developers to build highly reliable applications and workflows. As an orchestration engine, Conductor:

* Keeps track of your application’s execution flow and state;  
* Calls modular, stateless tasks in its defined order;  
* Handles failure scenarios and retries gracefully; and  
* Stores all final and intermediate results.

![Workflow screnshot](../../home/devex.png)

By using Conductor to build application flows, developers can focus on their core work: writing application code in the language of the choice, rather than orchestration or plumbing logic. Conductor does the heavy lifting associated with ensuring reliability, transactional consistency, and durability of the execution flow. No matter where your application code lives, you can build a workflow in Conductor to govern its execution.

## Features

Here are the key features available in Conductor OSS:

* A distributed server ecosystem, which stores workflow state information efficiently.  
* DAG- (Directed Acyclic Graph) based JSON workflow definitions, decoupled from its service implementations.  
* Reusable, built-in tasks and operators for quick workflow creation.  
* Visual diagrams for depicting workflow definitions and their runtime execution paths.  
* Logs, timelines, and execution data that provide visibility and traceability into the workflows.  
* Simple interface to connect Conductor server to workers that execute the tasks in workflows.  
* Language-agnostic workers, enabling each task to be written in the language most suited for the service.  
* Configurable properties with sensible defaults for rate limits, retries, timeouts, and concurrent execution limits.  
* User interface to build, search, run, and visualize workflows.  
* Full operational control over workflow execution (pause, resume, restart, retry and terminate).  
* Event handlers to control workflows via external actions.  
* Client implementations in Java, Python and other languages.  
* Backed by a queuing service abstracted from the clients.  
* Ability to scale to millions of concurrently-running process flows.

## Why not peer-to-peer choreography?

Using peer-to-peer task choreography, we found it harder to scale processes with evolving business needs and complexities. Even using a pub/sub model for the simplest flows, some of the issues associated with the approach quickly became evident:

* High-level process flows are "embedded" and implicit within the code of multiple applications.
* Often, there are many assumptions around inputs/outputs, SLAs, and so on, and the tight coupling makes it harder to adapt processes to changing needs.
* There is almost no way to systematically answer: "How far along are with in Process X?"

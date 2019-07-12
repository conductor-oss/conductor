<center>![Logo](img/conductor-vector-x.png)</center>
<center>![Logo](img/corner-logo-oss.png)</center>
<center>**Conductor is a _Workflow Orchestration engine_ that runs in the cloud.** </center>

## Motivation

We built Conductor to help us orchestrate microservices based process flows at Netflix with the following features:

* A distributed server ecosystem, which stores workflow state information efficiently.
* Allow creation of process / business flows in which each individual task can be implemented by the same / different microservices.
* A JSON DSL based blueprint defines the execution flow.
* Provide visibility and traceability into these process flows.
* Simple interface to connect workers, which execute the tasks in workflows.
* Full operational control over workflows with the ability to pause, resume, restart, retry and terminate.
* Allow greater reuse of existing microservices providing an easier path for onboarding.
* User interface to visualize, replay and search the process flows.
* Ability to scale to millions of concurrently running process flows.
* Backed by a queuing service abstracted from the clients.
* Be able to operate on HTTP or other transports e.g. gRPC.
* Event handlers to control workflows via external actions.
* Client implementations in Java, Python and other languages.
* Various configurable properties with sensible defaults to fine tune workflow and task executions like rate limiting, concurrent execution limits etc.

**Why not peer to peer choreography?**

With peer to peer task choreography, we found it was harder to scale with growing business needs and complexities.
Pub/sub model worked for simplest of the flows, but quickly highlighted some of the issues associated with the approach:

* Process flows are “embedded” within the code of multiple application.
* Often, there is tight coupling and assumptions around input/output, SLAs etc, making it harder to adapt to changing needs.
* Almost no way to systematically answer “How much are we done with process X”?

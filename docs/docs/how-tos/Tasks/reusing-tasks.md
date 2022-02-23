---
sidebar_position: 1
---

# Reusing Tasks

A powerful feature of Conductor is that it supports and enables re-usability out of the box. Task workers typically
perform a unit of work and is usually a part of a larger workflow. Such workers are often re-usable in multiple
workflows. Once a task is defined, you can use it across as any workflow.

When re-using tasks, it's important to think of situations that a multi-tenant system faces. All the work assigned to
this worker by default goes to the same task scheduling queue. This could result in your worker not being polled quickly
if there is a noisy neighbour in the ecosystem. One way you can tackle this situation is by re-using the worker code,
but having different task names registered for different use cases. And for each task name, you can run an appropriate
number of workers based on expected load.



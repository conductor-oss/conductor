# Basic Concepts
Conductor allows you to build a complex application using simple and granular tasks that do not
need to be aware of or keep track of the state of your application's execution flow. Conductor keeps track of the state,
calls tasks in the right order (sequentially or in parallel, as defined by you), retry calls if needed, handle failure
scenarios gracefully, and outputs the final result.


![Workflow screnshot](../../home/devex.png)

Leveraging workflows in Conductor enables developers to truly focus on their core mission - building their application
code in the languages of their choice. Conductor does the heavy lifting associated with ensuring high
reliability, transactional consistency, and long durability of their workflows. Simply put, wherever your application's
component lives and whichever languages they were written in, you can build a workflow in Conductor to orchestrate their
execution in a reliable & scalable manner.

[Workflows](workflows.md) and [Tasks](tasks.md) are the two key concepts that underlie the Conductor system. 


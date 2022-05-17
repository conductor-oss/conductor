---
sidebar_position: 1
---

# Dynamic vs Switch Tasks

Learn more about

1. [Dynamic Tasks](/reference-docs/dynamic-task.html)
2. [Switch Tasks](/reference-docs/switch-task.html)

Dynamic Tasks are useful in situations when need to run a task of which the task type is determined at runtime instead
of during the configuration. It is similar to the [SWITCH](/reference-docs/switch-task.html) use case but with `DYNAMIC`
we won't need to preconfigure all case options in the workflow definition itself. Instead, we can mark the task
as `DYNAMIC` and determine which underlying task does it run during the workflow execution itself.

1. Use DYNAMIC task as a replacement for SWITCH if you have too many case options
2. DYNAMIC task is an option when you want to programmatically determine the next task to run instead of using expressions
3. DYNAMIC task simplifies the workflow execution UI view which will now only show the selected task
4. SWITCH task visualization is helpful as a documentation - showing you all options that the workflow could have
   taken
5. SWITCH task comes with a default task option which can be useful in some use cases

# Operators

Operators are built-in primitives in Conductor that allow you to define the workflow's control flow. They are similar to programming constructs such as _for loops_, _if-else selections_, and so on. Conductor supports most programming primitives, so that you can create various advanced workflows.

Here are the operators available in Conductor OSS: 

| Operator                        | Description         |
| -------------------------- | ----------------------------------------- |
| [Do While](do-while-task.md)         | Do-while loops / For loops      | 
| [Dynamic](dynamic-task.md)           | Function pointer           | 
| [Dynamic Fork](dynamic-fork-task.md) | Dynamic parallel execution |
| [Fork](fork-task.md)                 | Static parallel execution  | 
| [Join](join-task.md)                 | Map                        |
| [Set Variable](set-variable-task.md)     | Workflow variable declaration           |
| [Start Workflow](start-workflow-task.md) | Entry point   | 
| [Sub Workflow](sub-workflow-task.md) | Subroutine  | 
| [Switch](switch-task.md)             | Switch / If..then...else selection     | 
| [Terminate](terminate-task.md)       | Exit                       |

The following operators are deprecated:

- Decision
- Exclusive Join
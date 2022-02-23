---
sidebar_position: 1
---

# Handling Errors

When a workflow fails, there are 2 ways to handle the exception.

## ```failureWorkflow```

In your main workflow definition, you can configure a workflow to run upon failure.  By default, three parameters are passed:

* reason
* workflowId: use this to pull the details of the failed workflow.
* failureStatus


##  Set ```workflowStatusListenerEnabled``` 

When this is enabled, notifications are now possible, and by building a custome implementation of the Workflow Status Listener, a notifictaion can be sent to an external service. [More details.](https://github.com/Netflix/conductor/issues/1017#issuecomment-468869173)
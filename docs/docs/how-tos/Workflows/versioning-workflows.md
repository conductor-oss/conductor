---
sidebar_position: 1
---

# Versioning Workflows

Every workflow has a version number (this number **must** be an integer.)

Versioning allows you to run different versions of the same workflow simultaneously.


## Summary

> Use Case:  A new version of your core workflow will add a capability that is required for *veryImportantCustomer*.  However, *otherVeryImportantCustomer* will not be ready to implement this code for another 6 months.


## Version 1

```json
{
  "name": "Core_workflow",
  "description": "Very_important_business",
  "version": 1,
  "tasks": [
    {
        <list of tasks>
    }
  ],
  "outputParameters": {
  }
}
```

## Version 2

```json
{
  "name": "Core_workflow",
  "description": "Very_important_business",
  "version": 2,
  "tasks": [
    {
        <a different list of tasks>
    }
  ],
  "outputParameters": {
  }
}
```

### Version 2 launch
Initially, both customers are on version 1 of the workflow.

* **veryImportantCustomer* may begin transitioning traffic onto version 2.  Any tasks that remain unfinished on version 1 *stay* on version 1.  
* *otherVeryImportantCustomer* remains on version 1.


### 6 months later

* All *veryImportantCustomer* workflows are on version 2.
* *otherVeryImportantCustomer* may begin transitioning traffic onto version 2.  Any tasks that remain unfinished on version 1 *stay* on version 1. 
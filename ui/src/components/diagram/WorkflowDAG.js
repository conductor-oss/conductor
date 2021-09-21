import _ from "lodash";
import { graphlib } from "dagre-d3";

export default class WorkflowDAG {
  constructor(execution, workflowDef) {
    this.execution = execution;
    this.workflowDef = workflowDef;

    this.graph = new graphlib.Graph({ directed: true, compound: false });
    this.taskResults = new Map();

    this.constructGraph();
  }

  addTaskResult(ref, task) {
    if (!this.taskResults.has(ref)) {
      this.taskResults.set(ref, []);
    }
    this.taskResults.get(ref).push(task);
  }

  getLastTaskResult(ref) {
    if (this.taskResults.has(ref)) {
      return _.last(this.taskResults.get(ref));
    } else {
      return null;
    }
  }

  constructGraph() {
    const { workflowDef, execution } = this;

    // Definition Only
    if (workflowDef) {
      this.defToGraph(workflowDef);
    }
    // Definition part of Execution object
    else if (execution) {
      let isTerminated = false;
      for (let task of execution.tasks) {
        if (task.taskType === "TERMINATE") isTerminated = true;

        this.addTaskResult(task.referenceTaskName, task);
      }

      if (execution.status) {
        this.addTaskResult("__start", { status: "COMPLETED" });
        if (execution.status === "COMPLETED" && !isTerminated) {
          this.addTaskResult("__final", { status: "COMPLETED" });
        }
      }

      this.defToGraph(execution.workflowDefinition);
    } else {
      throw new Error(
        "Must pass either workflowDef or execution in constructor"
      );
    }
  }

  defToGraph(workflowDef) {
    const definedTasks = workflowDef.tasks;

    definedTasks.unshift({
      type: "TERMINAL",
      name: "start",
      taskReferenceName: "__start",
    });

    definedTasks.push({
      type: "TERMINAL",
      name: "final",
      taskReferenceName: "__final",
    });

    // Recursively process tasks
    this.processTaskList(definedTasks, []);

    // All branches are terminated by a user-defined 'TERMINATE' task.
    if (_.isEmpty(this.graph.inEdges("__final"))) {
      this.graph.removeNode("__final");
    }
  }

  getExecutionStatus(ref) {
    const taskResult = this.getLastTaskResult(ref);
    if (taskResult) {
      return taskResult.status;
    } else {
      return null;
    }
  }

  isTakenDecisionBranch(caseValue, decisionTaskRef) {
    if (!this.taskResults.has(decisionTaskRef)) return false;

    const decisionTaskResult = this.getLastTaskResult(decisionTaskRef);
    const cases = Object.keys(decisionTaskResult.workflowTask.decisionCases);

    const actualValue = _.get(decisionTaskResult, "outputData.caseOutput[0]");
    if (actualValue === undefined) return false;

    if (caseValue) {
      // This is a case branch. Check whether it is the right one.
      return caseValue === actualValue;
    } else {
      // This is the default branch (caseValue === null). Check whether actual value is in one of the other cases
      return !cases.includes(actualValue);
    }
  }

  addVertex(taskConfig, antecedents) {
    const taskResults = this.taskResults.get(taskConfig.taskReferenceName);
    const lastTaskResult = _.last(taskResults);
    const vertex = {
      taskResults: taskResults || [
        {
          workflowTask: taskConfig,
        },
      ],
      name: taskConfig.name,
      ref: taskConfig.taskReferenceName,
      type: taskConfig.type,
      description: taskConfig.description,
    };
    if (taskConfig.dfTasks) {
      vertex.dfTasks = taskConfig.dfTasks;
    }

    if (lastTaskResult) {
      vertex.status = lastTaskResult.status;
    }

    this.graph.setNode(taskConfig.taskReferenceName, vertex);
    for (let antecedent of antecedents) {
      const antecedentExecuted = !!this.getExecutionStatus(
        antecedent.taskReferenceName
      );
      const edgeParams = {};

      // Special case - When the antecedent of an executed node is a DECISION, the edge may not necessarily be highlighted.
      // E.g. the default edge not taken.

      if (antecedent.type === "DECISION") {
        edgeParams.caseValue = getCaseValue(
          taskConfig.taskReferenceName,
          antecedent
        );

        // Highlight edge as executed only after thorough test
        const branchTaken = this.isTakenDecisionBranch(
          edgeParams.caseValue,
          antecedent.taskReferenceName
        );
        if (branchTaken) {
          edgeParams.executed = true;
        }
      } else if (
        lastTaskResult &&
        lastTaskResult.status &&
        antecedentExecuted
      ) {
        edgeParams.executed = true;
      }

      this.graph.setEdge(
        antecedent.taskReferenceName,
        taskConfig.taskReferenceName,
        edgeParams
      );
    }
  }

  processTaskList(tasks, antecedents) {
    console.assert(Array.isArray(antecedents));

    let currAntecedents = antecedents;
    for (const task of tasks.values()) {
      currAntecedents = this.processTask(task, currAntecedents);
    }

    return currAntecedents;
  }

  // Nodes are connected to previous
  processDecisionTask(decisionTask, antecedents) {
    console.assert(Array.isArray(antecedents));
    const retval = [];

    this.addVertex(decisionTask, antecedents);

    if (_.isEmpty(decisionTask.defaultCase)) {
      retval.push(decisionTask); // Empty default path
    } else {
      retval.push(
        ...this.processTaskList(decisionTask.defaultCase, [decisionTask], null)
      );
    }

    retval.push(
      ..._.flatten(
        Object.entries(decisionTask.decisionCases).map(([caseValue, tasks]) => {
          return this.processTaskList(tasks, [decisionTask]);
        })
      )
    );

    return retval;
  }

  processForkJoinDynamic(dfTask, antecedents) {
    console.assert(Array.isArray(antecedents));

    // This is the DF task (dotted bar) itself.
    this.addVertex(dfTask, antecedents);

    // Only add placeholder if there are 0 spawned tasks for this DF
    const dfTaskResult = this.getLastTaskResult(dfTask.taskReferenceName);
    const forkedTasks = _.get(dfTaskResult, "inputData.forkedTaskDefs");
    const forkedTasksCount = _.get(forkedTasks, "length");

    if (!forkedTasksCount) {
      const placeholderRef = dfTask.taskReferenceName + "_DF_EMPTY_PLACEHOLDER";

      const placeholderTask = {
        name: placeholderRef, // will be overwritten if results available
        taskReferenceName: placeholderRef, // will be overwritten if results available
        type: "DF_EMPTY_PLACEHOLDER",
      };

      if (_.get(dfTaskResult, "status")) {
        // Edge case: Backfill placeholder status for 'no tasks spawned'.
        this.addTaskResult(placeholderRef, { status: dfTaskResult.status });
      }

      this.addVertex(placeholderTask, [dfTask]);
      return [placeholderTask];
    } else {
      return dfTaskResult.inputData.forkedTaskDefs.map((task) => {
        this.addVertex(task, [dfTask]);
        return task;
      });
    }
  }

  processForkJoin(forkJoinTask, antecedents) {
    let outerForkTasks = forkJoinTask.forkTasks || [];

    // Add FORK_JOIN task itself (solid bar)
    this.addVertex(forkJoinTask, antecedents);

    // Each sublist is executed in parallel. Tasks within sublist executed sequentially
    return _.flatten(
      outerForkTasks.map((innerForkTasks) =>
        this.processTaskList(innerForkTasks, [forkJoinTask])
      )
    );
  }

  // returns tails = [...]
  processTask(task, antecedents) {
    switch (task.type) {
      case "FORK_JOIN": {
        return this.processForkJoin(task, antecedents);
      }

      case "FORK_JOIN_DYNAMIC": {
        return this.processForkJoinDynamic(task, antecedents);
      }

      case "DECISION": {
        return this.processDecisionTask(task, antecedents);
      }

      case "TERMINATE": {
        this.addVertex(task, antecedents);
        return [];
      }

      /*
      case "TERMINAL":
      case "JOIN":
      case "EVENT":
      case "SUB_WORKFLOW":
      case "EXCLUSIVE_JOIN":
      */
      default: {
        this.addVertex(task, antecedents);
        return [task];
      }
    }
  }

  dfChildInfo(ref) {
    const predecessors = this.graph.predecessors(ref);

    // Nodes might have multiple predecessors e.g. following Decision node.
    // But when parent is FORK_JOIN_DYNAMIC there should only be one.
    if (_.size(predecessors) === 1) {
      const parent = this.graph.node(_.first(predecessors));
      if (parent && parent.type === "FORK_JOIN_DYNAMIC") {
        return this.graph
          .successors(parent.ref)
          .map((ref) => this.graph.node(ref));
      }
    }
    // Returns undefined
  }
}

function getCaseValue(ref, decisionTask) {
  for (const [caseValue, taskList] of Object.entries(
    decisionTask.decisionCases
  )) {
    if (!_.isEmpty(taskList) && ref === taskList[0].taskReferenceName) {
      return caseValue;
    }
  }

  return null;
}

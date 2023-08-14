import _ from "lodash";
import { graphlib } from "dagre-d3";

export default class WorkflowDAG {
  constructor(execution, workflowDef) {
    this.execution = execution;
    this.workflowDef = workflowDef;

    this.graph = new graphlib.Graph({ directed: true, compound: false });
    this.taskResultsByRef = new Map();
    this.taskResultsById = new Map();

    this.constructGraph();
  }

  addTaskResult(ref, task) {
    if (!this.taskResultsByRef.has(ref)) {
      this.taskResultsByRef.set(ref, []);
    }
    this.taskResultsByRef.get(ref).push(task);
    this.taskResultsById.set(task.taskId, task);
  }

  getLastTaskResult(ref) {
    if (this.taskResultsByRef.has(ref)) {
      return _.last(this.taskResultsByRef.get(ref));
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
        if (task["taskType"] === "TERMINATE") isTerminated = true;

        this.addTaskResult(task["referenceTaskName"], task);
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
    const definedTasks = [...workflowDef.tasks];

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

  switchBranchTaken(caseValue, decisionTaskRef, type) {
    if (!this.taskResultsByRef.has(decisionTaskRef)) return false;

    const switchTaskResult = this.getLastTaskResult(decisionTaskRef);
    const cases = Object.keys(switchTaskResult.workflowTask.decisionCases);

    // Required until DECISION is fully removed
    let resultField = type === "SWITCH" ? "evaluationResult" : "caseOutput";
    const actualValue = _.get(switchTaskResult, `outputData.${resultField}[0]`);
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
    const taskResults = taskConfig.aliasForRef
      ? this.taskResultsByRef.get(taskConfig.aliasForRef)
      : this.taskResultsByRef.get(taskConfig.taskReferenceName);
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
      aliasForRef: taskConfig.aliasForRef,
    };

    if (lastTaskResult) {
      vertex.status = lastTaskResult.status;
    }

    this.graph.setNode(taskConfig.taskReferenceName, vertex);
    for (let antecedent of antecedents) {
      const antecedentExecuted = !!this.getExecutionStatus(
        antecedent.aliasForRef || antecedent.taskReferenceName
      );
      const edgeParams = {};

      // Special case - When the antecedent of an executed node is a SWITCH, the edge may not necessarily be highlighted.
      // E.g. the default edge not taken.
      //
      // SWITCH is the newer version of DECISION and DECISION is deprecated
      //
      // Skip this if current type is DO_WHILE_END - which means the SWITCH is one of the bundled
      // loop tasks and the current task is not the result of a decision
      if (
        taskConfig.type !== "DO_WHILE_END" &&
        (antecedent.type === "SWITCH" || antecedent.type === "DECISION")
      ) {
        edgeParams.caseValue = getCaseValue(
          taskConfig.taskReferenceName,
          antecedent
        );

        // Highlight edge as executed only after thorough test
        const branchTaken = this.switchBranchTaken(
          edgeParams.caseValue,
          antecedent.taskReferenceName,
          antecedent.type
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
  processSwitchTask(decisionTask, antecedents) {
    console.assert(Array.isArray(antecedents));
    const retval = [];

    this.addVertex(decisionTask, antecedents);

    if (_.isEmpty(decisionTask.defaultCase)) {
      retval.push(decisionTask); // Empty default path
    } else {
      retval.push(
        ...this.processTaskList(decisionTask.defaultCase, [decisionTask])
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

  getRefTaskChilds(task) {
    switch (task.type) {
      case "FORK_JOIN": {
        const outerForkTasks = task.forkTasks || [];
        return _.flatten(
          outerForkTasks.map((innerForkTasks) =>
            innerForkTasks.map((tasks) => tasks)
          )
        );
      }

      case "FORK_JOIN_DYNAMIC": {
        const dfTaskResult = this.getLastTaskResult(task.taskReferenceName);
        const forkedTasks = _.get(dfTaskResult, "inputData.forkedTaskDefs");
        const forkedTasksCount = _.get(forkedTasks, "length");

        if (!forkedTasksCount) {
          const placeholderRef =
            task.taskReferenceName + "_DF_EMPTY_PLACEHOLDER";
          const placeholderTask = {
            name: placeholderRef, // will be overwritten if results available
            taskReferenceName: placeholderRef, // will be overwritten if results available
            type: "DF_EMPTY_PLACEHOLDER",
          };
          return [placeholderTask];
        } else {
          return dfTaskResult.inputData.forkedTaskDefs;
        }
      }

      case "DECISION": // DECISION is deprecated and will be removed in a future release
      case "SWITCH": {
        const retval = [];
        if (!_.isEmpty(task.defaultCase)) {
          retval.push(...this.getRefTask(task.defaultCase));
        }
        retval.push(
          ..._.flatten(
            Object.entries(task.decisionCases).map(([caseValue, tasks]) => {
              return tasks;
            })
          )
        );
        return retval;
      }

      case "DO_WHILE": {
        return task.loopOver;
      }

      /*
      case "TERMINATE": 
      case "JOIN": 
      case "TERMINAL":
      case "EVENT":
      case "SUB_WORKFLOW":
      case "EXCLUSIVE_JOIN":
      */
      default: {
        return [];
      }
    }
  }

  getRefTask(task) {
    const taskRefs = this.getRefTaskChilds(task)
      .map((t) => {
        return this.getRefTask(t);
      })
      .reduce((r, tasks) => {
        return r.concat(tasks);
      }, []);
    return [task].concat(taskRefs);
  }

  processDoWhileTask(doWhileTask, antecedents) {
    console.assert(Array.isArray(antecedents));

    const hasDoWhileExecuted = !!this.getExecutionStatus(
      doWhileTask.taskReferenceName
    );

    this.addVertex(doWhileTask, antecedents);

    // Bottom bar
    // aliasForRef indicates when the bottom bar is clicked one we should highlight the ref
    let endDoWhileTask = {
      type: "DO_WHILE_END",
      name: doWhileTask.name,
      taskReferenceName: doWhileTask.taskReferenceName + "-END",
      aliasForRef: doWhileTask.taskReferenceName,
    };

    const loopOverRefPrefixes = this.getRefTask(doWhileTask).map(
      (t) => t.taskReferenceName
    );
    if (hasDoWhileExecuted) {
      // Create cosmetic LOOP edges between top and bottom bars
      this.graph.setEdge(
        doWhileTask.taskReferenceName,
        doWhileTask.taskReferenceName + "-END",
        {
          type: "loop",
          executed: hasDoWhileExecuted,
        }
      );

      const loopOverRefs = Array.from(this.taskResultsByRef.keys()).filter(
        (key) => {
          for (let prefix of loopOverRefPrefixes) {
            if (key.startsWith(prefix + "__")) return true;
          }
          return false;
        }
      );

      const loopTaskResults = [];
      for (let ref of loopOverRefs) {
        const refList = this.taskResultsByRef.get(ref);
        loopTaskResults.push(...refList);
      }

      const loopTasks = loopTaskResults.map((task) => ({
        name: task.taskDefName,
        taskReferenceName: task.referenceTaskName,
        type: task.taskType,
      }));

      for (let task of loopTasks) {
        this.addVertex(task, [doWhileTask]);
      }

      this.addVertex(endDoWhileTask, [...loopTasks]);
    } else {
      // Definition view (or not executed)

      this.processTaskList(doWhileTask.loopOver, [doWhileTask]);

      const lastLoopTask = _.last(doWhileTask.loopOver);

      // Connect the end of each case to the loop end
      if (
        lastLoopTask?.type === "SWITCH" ||
        lastLoopTask?.type === "DECISION"
      ) {
        Object.entries(lastLoopTask.decisionCases).forEach(
          ([caseValue, tasks]) => {
            const lastTaskInCase = _.last(tasks);
            this.addVertex(endDoWhileTask, [lastTaskInCase]);
          }
        );
      }

      // Default case
      this.addVertex(endDoWhileTask, [lastLoopTask]);
    }

    // Create reverse loop edge
    this.graph.setEdge(
      doWhileTask.taskReferenceName,
      doWhileTask.taskReferenceName + "-END",
      {
        type: "loop",
        executed: hasDoWhileExecuted,
      }
    );

    return [endDoWhileTask];
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

  processJoin(joinTask, antecedents) {
    // Process as a normal node UNLESS in special case of an externalized dynamic-fork. In which case - backfill spawned children.

    const taskResult = _.last(
      this.taskResultsByRef.get(joinTask.taskReferenceName)
    );
    const backfilled = [];
    const antecedent = _.first(antecedents);

    if (_.has(taskResult, "inputData.joinOn")) {
      const backfillRefs = taskResult.inputData.joinOn;
      if (_.get(antecedent, "type") === "DF_EMPTY_PLACEHOLDER") {
        const twoBeforeRef = _.first(
          this.graph.predecessors(antecedent.taskReferenceName)
        );
        const twoBefore = this.graph.node(twoBeforeRef);
        if (_.get(twoBefore, "type") === "FORK_JOIN_DYNAMIC") {
          console.log("Special case - backfill for externalized DYNAMIC_FORK");

          const twoBeforeDef = _.first(twoBefore.taskResults).workflowTask;
          for (let ref of backfillRefs) {
            const tasks = this.taskResultsByRef.get(ref);
            for (let task of tasks) {
              this.addVertex(task.workflowTask, [twoBeforeDef]);
              backfilled.push(task.workflowTask);
            }
          }
        }
      }
    }

    if (backfilled.length > 0) {
      // Remove placeholder if needed
      this.graph.removeNode(antecedent.taskReferenceName);

      // backfilled nodes converge onto join
      this.addVertex(joinTask, backfilled);
    } else {
      this.addVertex(joinTask, antecedents);
    }

    return [joinTask];
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

      case "DECISION": // DECISION is deprecated and will be removed in a future release
      case "SWITCH": {
        return this.processSwitchTask(task, antecedents);
      }

      case "TERMINATE": {
        this.addVertex(task, antecedents);
        return [];
      }

      case "DO_WHILE": {
        return this.processDoWhileTask(task, antecedents);
      }

      case "JOIN": {
        return this.processJoin(task, antecedents);
      }
      /*
      case "TERMINAL":
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

  getSiblings(taskPointer) {
    let ref;
    if (taskPointer.id) {
      const taskResult = this.taskResultsById.get(taskPointer.id);
      if (taskResult) {
        ref = taskResult.referenceTaskName;
      }
    } else {
      ref = taskPointer.ref;
    }

    if (!ref) return;

    const predecessors = this.graph.predecessors(ref);
    // Nodes might have multiple predecessors e.g. following Decision node.
    // But when parent is FORK_JOIN_DYNAMIC there should only be one.
    if (_.size(predecessors) === 1) {
      const parent = this.graph.node(_.first(predecessors));
      if (parent && parent.status) {
        if (parent.type === "FORK_JOIN_DYNAMIC") {
          return this.graph
            .successors(parent.ref)
            .map((ref) => this.graph.node(ref));
        } else if (parent.type === "DO_WHILE") {
          return this.graph
            .successors(parent.ref)
            .map((ref) => this.graph.node(ref))
            .filter((node) => node.type !== "DO_WHILE_END");
        }
      }
    }
    // Returns undefined
  }

  findTaskResultById(id) {
    return this.taskResultsById.get(id);
  }

  getRetries(taskPointer) {
    if (taskPointer.id) {
      const taskResult = this.taskResultsById.get(taskPointer.id);
      if (taskResult) {
        const ref = taskResult.referenceTaskName;
        return this.taskResultsByRef.get(ref);
      }
    } else {
      return this.taskResultsByRef.get(taskPointer.ref);
    }
  }

  resolveTaskResult(taskPointer) {
    if (!taskPointer) {
      return null;
    } else if (taskPointer.id) {
      return this.taskResultsById.get(taskPointer.id);
    } else {
      const node = this.graph.node(taskPointer.ref);
      return _.last(node.taskResults);
    }
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

/*

Node {
  taskResults: [... TaskResult]
}

TaskResult {
  ...[Task Result fields only present if executed],
  workflowTask: {
    ... Always populated
  }
}

*/

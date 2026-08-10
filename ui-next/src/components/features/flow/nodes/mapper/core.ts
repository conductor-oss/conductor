import _property from "lodash/property";
import _first from "lodash/first";
import _isUndefined from "lodash/isUndefined";
import _mapValues from "lodash/mapValues";
import { edgeMapper } from "./edgeMapper";
import { taskToSwitchNodesEdges } from "./switch";
import { taskToNode, maybeEdgeData } from "./common";
import { taskToForkJoinNodesEdges } from "./forkJoin";
import { processDoWhile } from "./doWhile";
import { taskToTerminateNode } from "./terminate";
import { taskToForkJoinDynamicNodesEdges } from "./forkJoinDynamic";
import { joinTasksToNodesEdges } from "./join";
import { processSubWorkflow } from "./subWorkflow";
import { NodeData } from "reaflow";
import {
  processLastTask,
  endNode,
  startNode,
  firstTask as firstFakeTask,
} from "./terminal";
import {
  CommonTaskDef,
  TaskType,
  Crumb,
  WorkflowDef,
  TaskStatus,
  WorkflowExecutionStatus,
} from "types";
import { NodeTaskData, EdgeTaskData, SubWorkflowFunction } from "./types";

import {
  isJoinTask,
  isForkJoinTask,
  isForkJoinDynamicTask,
  isDoWhileTask,
  isTerminateTask,
  isSubWorkflowTask,
  isSwitchTask,
  isForkableTask,
} from "./predicates";

export const extractTaskReferenceName = (tasks: {
  taskReferenceName: string;
}) => Object.values(tasks).map(_property("taskReferenceName"));

type Accumulator = {
  nodes: NodeData<NodeTaskData>[];
  edges: EdgeTaskData[];
  crumbs: Crumb[];
  previousTask?: CommonTaskDef;
  previousTaskAllowsConnection: boolean; // deprecated
};
type TasksAsNodesProps = {
  tasks?: NodeData<NodeTaskData>[];
  edges?: EdgeTaskData[];
  crumbs?: Crumb[];
  crumbContext?: Partial<Crumb>;
  expandSubWorkflow?: boolean;
  subWorkFlowFetcher?: SubWorkflowFunction;
  readOnly?: boolean;
};

type TaskWalkerFn = (
  t: CommonTaskDef[],
  tanProps: TasksAsNodesProps,
) => Promise<Accumulator>;

const mergeCur = (destination: Accumulator) => (source: Partial<Accumulator>) =>
  ({ ...destination, ...source }) as Accumulator;
export const tasksAsNodes: TaskWalkerFn = async (
  mappableTasks: CommonTaskDef[],
  {
    tasks: initialTasks = [],
    edges: initialEdges = [],
    crumbs: initialCrumbs = [],
    crumbContext = {
      parent: null,
    },
    expandSubWorkflow = true,
    subWorkFlowFetcher = async (_workflowName: string, _version?: number) =>
      Promise.resolve({ tasks: [] }),
    readOnly = false,
  }: TasksAsNodesProps = {
    tasks: [],
    edges: [],
    crumbs: [],
    crumbContext: {
      parent: null,
    },
    readOnly: false,
  },
) => {
  let acc: Accumulator = {
    nodes: initialTasks,
    edges: initialEdges,
    previousTask: undefined,
    crumbs: initialCrumbs,
    previousTaskAllowsConnection: false,
  };

  for (const [idx, currentTask] of mappableTasks.entries()) {
    const { type, taskReferenceName } = currentTask;

    const crumbs = acc.crumbs.concat({
      ...crumbContext,
      ref: taskReferenceName,
      refIdx: idx,
      type,
    });

    let processedResult: Accumulator = {
      nodes: acc.nodes,
      edges: acc.edges,
      previousTask: currentTask,
      crumbs,
      previousTaskAllowsConnection: true,
    };

    const updatePr = mergeCur(processedResult);
    // task walker with current subworkflow props
    const taskWalkerFunc: TaskWalkerFn = (tasksP, tanProps) =>
      tasksAsNodes(tasksP, {
        expandSubWorkflow,
        subWorkFlowFetcher,
        readOnly,
        ...tanProps,
      });

    if (isJoinTask(currentTask)) {
      if (acc.previousTask) {
        const previousTask = acc.previousTask;
        const { nodes: joinNodes, edges: joinEdges } = joinTasksToNodesEdges(
          currentTask,
          previousTask,
          crumbs,
          acc.nodes,
        );

        // Update joinOn to point to the last node
        // if the previous node was joined
        if (isForkableTask(previousTask) && previousTask?.forkTasks?.length) {
          previousTask.forkTasks.forEach((forkTask) => {
            if (forkTask.length < 2) return;
            const lastForkTask = forkTask[forkTask.length - 1];
            const nodeBeforeLastForkTask = forkTask[forkTask.length - 2];
            const isPreviousNodeJoinedOn = currentTask.joinOn.includes(
              nodeBeforeLastForkTask.taskReferenceName,
            );
            if (!isPreviousNodeJoinedOn) return;
            currentTask.joinOn = currentTask.joinOn.map((joinOnRefName) => {
              return joinOnRefName === nodeBeforeLastForkTask.taskReferenceName
                ? lastForkTask.taskReferenceName
                : joinOnRefName;
            });
          });
        }

        processedResult = updatePr({
          nodes: joinNodes,
          edges: acc.edges.concat(
            edgeMapper(
              currentTask,
              previousTask,
              acc.previousTaskAllowsConnection,
            ),
            joinEdges,
          ),
        });
      } else {
        // Join is the first task
        processedResult = updatePr({
          nodes: acc.nodes.concat(taskToNode(currentTask, crumbs)),
        });
      }
    } else if (isForkJoinTask(currentTask)) {
      const { nodes: procesedForkedNodes, edges: procesedForkedEdges } =
        await taskToForkJoinNodesEdges(currentTask, crumbs, taskWalkerFunc);

      processedResult = updatePr({
        nodes: acc.nodes.concat(procesedForkedNodes),
        edges: acc.edges.concat(
          edgeMapper(
            currentTask,
            acc?.previousTask,
            acc.previousTaskAllowsConnection,
          ),
          procesedForkedEdges,
        ),
      });
    } else if (isForkJoinDynamicTask(currentTask)) {
      const { nodes: forkJoinDynamicNodes, edges: forkJoinDynamicEdges } =
        await taskToForkJoinDynamicNodesEdges(
          currentTask,
          crumbs,
          taskWalkerFunc,
        );

      processedResult = updatePr({
        nodes: acc.nodes.concat(forkJoinDynamicNodes),
        edges: acc.edges.concat(
          edgeMapper(
            currentTask,
            acc?.previousTask,
            acc?.previousTaskAllowsConnection,
          ),
          forkJoinDynamicEdges,
        ),
      });
    } else if (isSwitchTask(currentTask)) {
      const {
        nodes: switchNodes,
        edges: switchEdges,
        everyTaskIsTerminate,
      } = await taskToSwitchNodesEdges(currentTask, crumbs, taskWalkerFunc);

      processedResult = updatePr({
        nodes: acc.nodes.concat(switchNodes),
        edges: acc.edges.concat(
          edgeMapper(
            currentTask,
            acc?.previousTask,
            acc?.previousTaskAllowsConnection,
          ),
          switchEdges,
        ),
        previousTask: currentTask,
        previousTaskAllowsConnection: !everyTaskIsTerminate,
      });
    } else if (isDoWhileTask(currentTask)) {
      const { nodes: doWhileNodes, edges: doWhileEdges } = await processDoWhile(
        currentTask,
        crumbs,
        taskWalkerFunc,
      );
      processedResult = updatePr({
        nodes: acc.nodes.concat(doWhileNodes),
        edges: acc.edges
          .concat(
            edgeMapper(
              currentTask,
              acc?.previousTask,
              acc?.previousTaskAllowsConnection,
            ),
          )
          .concat(doWhileEdges),
      });
    } else if (isTerminateTask(currentTask)) {
      processedResult = updatePr({
        nodes: acc.nodes.concat(taskToTerminateNode(currentTask, crumbs)),
        edges: acc.edges.concat(
          edgeMapper(
            currentTask,
            acc?.previousTask,
            acc?.previousTaskAllowsConnection,
          ),
        ),
        previousTask: currentTask,
        previousTaskAllowsConnection: false,
      });
    } else if (isSubWorkflowTask(currentTask) && expandSubWorkflow) {
      const { nodes: subWorkflowNodes, edges: subWorkflowEdges } =
        await processSubWorkflow(
          currentTask,
          crumbs,
          tasksAsNodes, // We don't want to mantain the subworkflow props since we want this only on the outer layer
          subWorkFlowFetcher,
        );

      processedResult = updatePr({
        nodes: acc.nodes.concat(subWorkflowNodes),
        edges: acc.edges
          .concat(
            edgeMapper(
              currentTask,
              acc?.previousTask,
              acc?.previousTaskAllowsConnection,
            ),
          )
          .concat(subWorkflowEdges),
      });
    } else {
      processedResult = updatePr({
        nodes: acc.nodes.concat(taskToNode(currentTask, crumbs)),
        edges: acc.edges.concat(
          edgeMapper(
            currentTask,
            acc?.previousTask,
            acc?.previousTaskAllowsConnection,
          ),
        ),
      });
    }

    acc = processedResult;
  }

  return acc;
};

const maybePrependFirstNode = (
  { nodes, edges }: { nodes: NodeData<NodeTaskData>[]; edges: EdgeTaskData[] },
  firstTask: CommonTaskDef,
) => {
  const firstNode = nodes.find(({ id }) => id === firstTask.taskReferenceName);
  return firstTask.type === TaskType.TERMINAL
    ? { nodes, edges }
    : {
        nodes: [startNode].concat(nodes),
        edges: [
          {
            id: `edge_start_${startNode.id}_${firstNode?.id}`,
            from: startNode.id,
            to: firstNode?.id,
            fromPort: `${startNode.id}-south-port`,
            toPort: `${firstNode?.id}-to`,
            ...maybeEdgeData(firstTask, {
              ...firstFakeTask,
              executionData:
                firstTask?.executionData != null
                  ? { status: TaskStatus.COMPLETED }
                  : undefined,
            }),
          },
          ...edges,
        ],
      };
};

export const workflowToNodeEdges = async (
  workflow: Partial<WorkflowDef>,
  showPorts = true,
  expandSubWorkflow = true,
  workflowFetcher: SubWorkflowFunction,
  workflowStatus: WorkflowExecutionStatus,
) => {
  const mappableTasks = workflow?.tasks || [];
  if (mappableTasks.length < 1) {
    return {
      nodes: [startNode, endNode],
      edges: [
        {
          id: `edge_start_${startNode.id}_${endNode.id}`,
          from: startNode.id,
          to: endNode.id,
          fromPort: `${startNode.id}-south-port`,
          toPort: `${endNode.id}-to`,
        },
      ],
    };
  }
  const firstTask = _first(mappableTasks);
  const taskAsNodesResult = await tasksAsNodes(mappableTasks, {
    subWorkFlowFetcher: workflowFetcher,
    expandSubWorkflow,
    readOnly: !showPorts,
  });

  const result = maybePrependFirstNode(
    processLastTask(taskAsNodesResult, workflowStatus),
    firstTask!,
  );

  return showPorts
    ? result
    : _mapValues(result, (arr) =>
        arr.map(({ ports, ...values }: any) => ({
          ...values,
          ports: _isUndefined(ports)
            ? undefined
            : ports.map((p: any) => ({ ...p, hidden: true })),
        })),
      );
};

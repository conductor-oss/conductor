import _isEmpty from "lodash/isEmpty";
import _head from "lodash/head";
import _pick from "lodash/pick";
import _findLast from "lodash/findLast";
import { ADD_TASK_ABOVE } from "pages/definition/state/taskModifier/constants";
import {
  extractExecutionDataOrEmpty,
  taskHasCompleted,
  edgeIdMapper,
  completedTaskStatusData,
} from "./common";
import { northPort, southPort } from "./ports";
import { taskToSize } from "./layout";
import { TaskType, SwitchTaskDef, Crumb, TaskDef, CommonTaskDef } from "types";
import { NodeData, EdgeData, PortData, PortSide } from "reaflow";
import { NodeTaskData, NodesAndEdges } from "./types";
import { isSwitchType, isSwitchTask } from "./predicates";

type DecisionBranches = {
  defaultCase: CommonTaskDef[];
  [k: string]: CommonTaskDef[];
};

/**
 * Takes a Switch returns an object with decisionCases and defaultCase
 * *NOTE* defaulCase is added last so that when turning tu entries defaultCase is last
 * @param switchTask
 * @returns
 */
export const switchTaskToDecisionsToProcess = (
  switchTask: SwitchTaskDef,
): DecisionBranches => {
  const { decisionCases, defaultCase = [] } = switchTask;

  const decisionBranches = {
    ...decisionCases,
    defaultCase,
  };
  return decisionBranches;
};

type NodesEdgesCrumbsPreviousTask = NodesAndEdges & {
  crumbs: Crumb[];
  previousTask: TaskDef;
  previousTaskAllowsConnection: boolean;
};

/**
 * Takes decisionBranches the switch task{taskReferenceName} initial crumbs
 * and a taskWalker. will return nodes,edges,crumbs,previous task by branch
 * @param switchTask
 * @param crumbs
 * @param taskWalkerFn
 * @returns
 */
export const decisionBranchesToNodesEdgesByCase = async (
  switchTask: SwitchTaskDef,
  crumbs: Crumb[],
  taskWalkerFn: any,
): Promise<{ [k: string]: NodesEdgesCrumbsPreviousTask }> => {
  const decisionBranches = switchTaskToDecisionsToProcess(switchTask);

  const decisionBranchEntries = Object.entries(decisionBranches);
  let acc = {};
  for (const [, [k, innerTasks]] of decisionBranchEntries.entries()) {
    acc = {
      ...acc,
      [k]: _pick(
        await taskWalkerFn(innerTasks, {
          crumbContext: {
            parent: switchTask?.taskReferenceName,
            decisionBranch: k,
          },
          crumbs,
        }),
        [
          "nodes",
          "edges",
          "crumbs",
          "previousTask",
          "previousTaskAllowsConnection",
        ],
      ),
    };
  }
  return acc;
};

export type ProcessedSwitchTask = CommonTaskDef & {
  allowsTaskConnection?: boolean;
};

type SwitchTaskNodesEdgesEndingTasksDecisionKeysEndingNodes = NodesAndEdges & {
  decisionKeys: string[];
  lastSwitchTasks: Array<ProcessedSwitchTask | undefined>;
  lastSwitchNodes: Array<NodeData | undefined>;
};

const switchMaybeEdgeData = (switchTask: SwitchTaskDef) => (path: string) => {
  const outputData = switchTask?.executionData?.outputData;

  if (!outputData) return {}; // Not an execution or not executed yet
  const decisionCases = Object.keys(switchTask?.decisionCases || []);
  const selectedCase =
    switchTask?.type === TaskType.SWITCH
      ? outputData?.selectedCase
      : outputData?.caseOutput?.toString();
  const hasPath = decisionCases.includes(path);
  const hasPathAndPathWasSelected = hasPath && path === selectedCase;
  const caseIsDefaultCase =
    !hasPath && path === "defaultCase" && !decisionCases.includes(selectedCase);
  return hasPathAndPathWasSelected || // selected case is a valid path
    caseIsDefaultCase
    ? completedTaskStatusData(false)
    : {};
};

/**
 * Returns every node that can be travered from the switchTask, every edge connected, every decision key
 * The last task of every branch. and the last switch node. will insert undefined if node is empty
 * so the order matches the decisionKeys
 *
 * @param switchTask
 * @param crumbs
 * @param taskWalkerFn
 * @returns
 */
export const processSwitchTasks = async (
  switchTask: SwitchTaskDef,
  crumbs: Crumb[],
  taskWalkerFn: any,
): Promise<SwitchTaskNodesEdgesEndingTasksDecisionKeysEndingNodes> => {
  const decisionBranchesAsNodeEdges = await decisionBranchesToNodesEdgesByCase(
    switchTask,
    crumbs,
    taskWalkerFn,
  );
  const decisionEntries = Object.entries(decisionBranchesAsNodeEdges);
  const maybeDataForSwitchPath = switchMaybeEdgeData(switchTask);

  return decisionEntries.reduce(
    (
      acc: SwitchTaskNodesEdgesEndingTasksDecisionKeysEndingNodes,
      [
        decisionKey,
        { nodes, edges, previousTask, previousTaskAllowsConnection },
      ],
    ) => {
      let switchTaskConnectingEdge: EdgeData[] = [];
      if (!_isEmpty(nodes)) {
        // Move this to a different function
        const { id: firstNodeId, data: firstNodeData } = _head(nodes)!;
        const firstTask = firstNodeData!.task;
        const edgeId: string = edgeIdMapper(
          switchTask as CommonTaskDef,
          firstTask,
        ) as string;
        switchTaskConnectingEdge = [
          {
            id: edgeId,
            from: switchTask.taskReferenceName,
            to: firstNodeId,
            fromPort: `${switchTask.taskReferenceName}_[key=${decisionKey}]-south-port`,
            toPort: `${switchTask.taskReferenceName}_[key=${decisionKey}]-south-port-to`,
            text: decisionKey,
            data: {
              ...maybeDataForSwitchPath(decisionKey),
              action: ADD_TASK_ABOVE,
            },
          },
        ];
      }
      return {
        edges: acc.edges.concat(switchTaskConnectingEdge).concat(edges),
        nodes: acc.nodes.concat(nodes),
        lastSwitchTasks: acc.lastSwitchTasks.concat(
          previousTask != null
            ? {
                ...previousTask,
                allowsTaskConnection: previousTaskAllowsConnection,
              }
            : undefined, // if empty return undefined this is intended. order is important
        ), // This tasks should not get into node-data
        decisionKeys: acc.decisionKeys.concat(decisionKey),
        lastSwitchNodes: acc.lastSwitchNodes.concat(
          _findLast(nodes, ({ id }) => id === previousTask?.taskReferenceName),
        ), // if nodes is empty. this will yield undefined. and its ok its a desired effect
      };
    },
    {
      nodes: [],
      edges: [],
      lastSwitchTasks: [],
      decisionKeys: [],
      lastSwitchNodes: [],
    },
  );
};

export const switchTaskToNode = (
  task: SwitchTaskDef,
  crumbs: Crumb[],
  decisionKeys: string[],
): NodeData<NodeTaskData<SwitchTaskDef>> => {
  const { taskReferenceName, name } = task;

  const ports = decisionKeys.map((k, idx) =>
    southPort({ id: `${taskReferenceName}_[key=${k}]` }, idx),
  );
  const switchSize = taskToSize(task);
  const node = {
    id: taskReferenceName,
    text: name,
    ports,
    data: {
      task,
      crumbs,
      ...extractExecutionDataOrEmpty(task),
    },
    ...switchSize,
  };
  return node;
};

type SwitchTaskDriller = (
  task: SwitchTaskDef,
  endLeafTasks: CommonTaskDef[],
) => Promise<ProcessedSwitchTask[]>;

/**
 * @deprecated This function made sense when no switch-join.
 * Returns a function that takes a task. Will look for non terminated tasks
 * used to identify missing connection edges
 * @param {*} tasksAsNodes
 * @returns
 */
export const drillForEndTasks = (tasksAsNodes: any): SwitchTaskDriller => {
  const inner: SwitchTaskDriller = async (
    task: SwitchTaskDef,
    endLeafTasks: ProcessedSwitchTask[] = [],
  ) => {
    const { lastSwitchTasks } = await processSwitchTasks(
      task,
      [],
      tasksAsNodes,
    );
    let acc: ProcessedSwitchTask[] = [];
    for (const [, endTask] of lastSwitchTasks.entries()) {
      if (isSwitchTask(endTask)) {
        acc = acc
          // .concat(_isEmpty(endTask.defaultCase) ? endTask : [])
          .concat(
            await inner(endTask as SwitchTaskDef, []),
          ) as ProcessedSwitchTask[];
      } else if (
        endTask?.type === TaskType.TERMINATE ||
        endTask?.type === TaskType.TERMINAL
      ) {
        // TODO
      } else {
        acc = acc.concat(
          endTask === undefined ? [] : endTask,
        ) as ProcessedSwitchTask[];
      }
    }
    return endLeafTasks.concat(acc);
  };
  return inner;
};

type SwitchTaskReferenceNonTerminatedReference = {
  switchTr: CommonTaskDef[];
  nonTerminatedTr: CommonTaskDef[];
};

export const nonTerminatedTasksGroupedAsTaskReferenceNameByType = (
  nonTerminatedTasks: TaskDef[],
) =>
  nonTerminatedTasks.reduce(
    (
      { switchTr, nonTerminatedTr }: SwitchTaskReferenceNonTerminatedReference,
      task,
    ) =>
      isSwitchType(task.type)
        ? { switchTr: switchTr.concat(task), nonTerminatedTr }
        : {
            switchTr,
            nonTerminatedTr: nonTerminatedTr.concat(task),
          },
    { switchTr: [], nonTerminatedTr: [] },
  );

export const switchTaskToFakeNodeId = ({ taskReferenceName }: SwitchTaskDef) =>
  `${taskReferenceName}_switch_join`;

export const switchFakeTaskIDSouthPortId = (fakeTaskId: string) =>
  `switch_fake_task_${fakeTaskId}-south-port`;

export const createFakeNode = (
  switchCaseTask: SwitchTaskDef,
  crumbs: Crumb[],
  decisionCasesKeys: string[],
): NodeData => {
  const id = switchTaskToFakeNodeId(switchCaseTask);
  const decisionCasesPorts: PortData[] = decisionCasesKeys.map((pk, idx) =>
    northPort(
      { id: `${id}-to-join-to=[key=${pk}]` },
      (decisionCasesKeys?.length || 1) - 1 - idx,
      true,
    ),
  );
  return {
    id,
    data: {
      task: {
        ...switchCaseTask,
        type: "SWITCH_JOIN",
      },
      crumbs,
      originalTask: switchCaseTask,
    },
    ports: [
      {
        id: switchFakeTaskIDSouthPortId(id),
        side: "SOUTH",
        disabled: true,
        width: 2,
        height: 2,
      } as PortData,
    ].concat(decisionCasesPorts),
    text: `${switchCaseTask.taskReferenceName}_switch_join`,
    ...taskToSize({ type: TaskType.SWITCH_JOIN }),
  };
};

export const lastNodeToFakeTaskEdge = (
  lastNode: NodeData<NodeTaskData>,
  switchNode: NodeData<NodeTaskData>,
  fakeNodeId: string,
  decisionBranch: string,
): EdgeData => {
  const lastNodeTask = lastNode.data?.task;
  const switchNodeTask = switchNode.data?.task;
  const switchNodeHasCompleted = taskHasCompleted(switchNodeTask);
  const maybeData =
    switchNodeHasCompleted && taskHasCompleted(lastNodeTask)
      ? { data: { status: lastNode?.data?.status } }
      : {};
  switch (lastNodeTask?.type) {
    case TaskType.DECISION:
    case TaskType.SWITCH: {
      // If last task is a switch we need to connect the pseudo task with the switch
      const lastSwitchTaskFakeNodeId = switchTaskToFakeNodeId(
        lastNodeTask as SwitchTaskDef,
      );
      return {
        id: `edge_dp__fake_${switchNode.id}_${lastNode.id}`,
        from: lastSwitchTaskFakeNodeId,
        fromPort: switchFakeTaskIDSouthPortId(lastSwitchTaskFakeNodeId),
        toPort: `${fakeNodeId}-to-join-to=[key=${decisionBranch}]-north-port`,
        to: fakeNodeId,
        ...maybeData,
      };
    }
    case TaskType.TERMINATE: {
      return {
        id: `edge_dp__fake_${switchNode.id}_${lastNode.id}`,
        from: lastNode.id,
        to: fakeNodeId,
        toPort: `${fakeNodeId}-to-join-to=[key=${decisionBranch}]-north-port`,
        data: { unreachableEdge: true },
      };
    }
    default: {
      const maybeSouthPort = lastNode?.ports?.find(
        (p) => p.side === ("SOUTH" as PortSide),
      );

      const portData =
        maybeSouthPort !== undefined
          ? {
              fromPort: maybeSouthPort.id,
            }
          : {};

      return {
        id: `edge_dp__fake_${switchNode.id}_${lastNode.id}`,
        from: lastNode.id,
        to: fakeNodeId,
        toPort: `${fakeNodeId}-to-join-to=[key=${decisionBranch}]-north-port`,
        ...portData,
        ...maybeData,
      };
    }
  }
};

export const switchFakeTaskEdges = (
  switchLastNodes: Array<NodeData | undefined>,
  lastSwitchTasks: Array<ProcessedSwitchTask | undefined>,
  switchCreatedNode: NodeData,
  decisionKeys: string[],
  fakeNode: NodeData,
  selectedCase?: string,
): EdgeData[] => {
  return decisionKeys.reduce((acc: EdgeData[], k, idx) => {
    const markPathAsCompleted =
      k &&
      ((taskHasCompleted(switchCreatedNode?.data?.task) &&
        _isEmpty(selectedCase) &&
        k === "defaultCase") ||
        (taskHasCompleted(switchCreatedNode?.data?.task) &&
          !decisionKeys.includes(selectedCase!) &&
          k === "defaultCase") ||
        k === selectedCase);
    if (switchLastNodes[idx] != null && lastSwitchTasks[idx] != null) {
      return acc.concat(
        lastNodeToFakeTaskEdge(
          switchLastNodes[idx]!,
          switchCreatedNode,
          fakeNode.id,
          k,
        ),
      );
    }
    return acc.concat({
      // if no node
      id: `edge_dp__fake_${switchCreatedNode.id}_${k}-direct`,
      from: switchCreatedNode.id,
      fromPort: `${switchCreatedNode.id}_[key=${k}]-south-port`,
      toPort: `${fakeNode.id}-to-join-to=[key=${k}]-north-port`,
      to: fakeNode.id,
      text: k,
      ...(markPathAsCompleted ? { data: { status: "COMPLETED" } } : {}),
    });
  }, []);
};

const isEveryPathInSwitchTerminated = (
  switchTaskResult: SwitchTaskNodesEdgesEndingTasksDecisionKeysEndingNodes,
): boolean => {
  const { lastSwitchTasks, decisionKeys } = switchTaskResult;

  return (
    !_isEmpty(lastSwitchTasks) &&
    decisionKeys.length === lastSwitchTasks.length &&
    lastSwitchTasks.every((task) => {
      return task?.allowsTaskConnection === false;
    })
  );
};

export const taskToSwitchNodesEdges = async (
  currentTask: SwitchTaskDef,
  crumbs: Crumb[],
  taskWalkerFn: any,
): Promise<NodesAndEdges & { everyTaskIsTerminate: boolean }> => {
  const switchResult = await processSwitchTasks(
    currentTask,
    crumbs,
    taskWalkerFn,
  );

  const {
    nodes: switchInnerNodes,
    edges: switchInnerEdges,
    decisionKeys,
    lastSwitchNodes,
    lastSwitchTasks,
  } = switchResult;

  const switchNode = switchTaskToNode(currentTask, crumbs, decisionKeys);

  const everyTaskIsTerminate = isEveryPathInSwitchTerminated(switchResult);

  let additionalNodes: NodeData[] = [];
  let additionalEdges: EdgeData[] = [];

  const fakeNode = createFakeNode(currentTask, crumbs, decisionKeys);
  const selectedCase =
    currentTask?.type === TaskType.SWITCH
      ? currentTask?.executionData?.outputData?.selectedCase
      : currentTask?.executionData?.outputData?.caseOutput?.toString();
  const fakeEdges: EdgeData[] = switchFakeTaskEdges(
    lastSwitchNodes,
    lastSwitchTasks,
    switchNode,
    decisionKeys,
    fakeNode,
    selectedCase,
  );
  additionalNodes = [fakeNode];
  additionalEdges = fakeEdges;

  const switchNodeArray: NodeData<NodeTaskData>[] = [switchNode];

  // Only needed in edit mode. not in execution mode
  return {
    nodes: switchNodeArray.concat(switchInnerNodes).concat(additionalNodes),
    edges: switchInnerEdges.concat(additionalEdges),
    everyTaskIsTerminate,
  };
};

export const isSwitchPathEmpty = (portId: string, currentTask: SwitchTaskDef) =>
  portId && currentTask && currentTask.decisionCases?.[portId]?.length === 0;

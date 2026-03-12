import _head from "lodash/head";
import _isEmpty from "lodash/isEmpty";
import { southPort } from "./ports";
import {
  extractExecutionDataOrEmpty,
  edgeIdMapper,
  maybeEdgeData,
} from "./common";
import { taskToSize } from "./layout"; // TODO maybe get rid of this.
import { ForkJoinTaskDef, Crumb, CommonTaskDef, ForkableTask } from "types";
import { NodeData, EdgeData } from "reaflow";
import { NodesAndEdges, NodeTaskData } from "./types";

export const innerTaskConnectingEdge = (
  taskHoldingTasks: CommonTaskDef,
  processedInnerNodes: NodeData[],
  suffix = "0",
): EdgeData => {
  const firstTask = _head(processedInnerNodes)!.data.task;
  return {
    id: `${edgeIdMapper(taskHoldingTasks, firstTask)}_${suffix}`,
    fromPort: `${taskHoldingTasks.taskReferenceName}_[key=${suffix}]-south-port`,
    toPort: `${firstTask.taskReferenceName}-to`,
    from: taskHoldingTasks.taskReferenceName,
    to: firstTask.taskReferenceName,
    ...maybeEdgeData(firstTask, taskHoldingTasks),
  };
};
export const processForkJoinTasks = async <T extends ForkableTask>(
  forkJoinTask: T,
  crumbs: Crumb[],
  taskWalkerFn: any,
): Promise<NodesAndEdges> => {
  const { forkTasks = [] } = forkJoinTask;
  let acc: NodesAndEdges = {
    nodes: [],
    edges: [],
  };
  for (const [idx, innerTasks] of forkTasks.entries()) {
    const { nodes, edges } = await taskWalkerFn(innerTasks, {
      crumbContext: {
        parent: forkJoinTask.taskReferenceName,
        forkIndex: idx,
      },
      crumbs,
    });
    const maybeConnectingEdge: EdgeData[] = _isEmpty(nodes)
      ? []
      : [innerTaskConnectingEdge(forkJoinTask, nodes, `${idx}`)];
    acc = {
      edges: acc.edges.concat(maybeConnectingEdge, edges),
      nodes: acc.nodes.concat(nodes),
    };
  }

  return acc;
};

export const forkJoinTaskToNode = <T extends ForkableTask>(
  task: T,
  crumbs: Crumb[],
): NodeData<NodeTaskData<T>> => {
  const { taskReferenceName, name, forkTasks = [] } = task;
  return {
    id: taskReferenceName,
    text: name,
    ports: forkTasks.map((_, idx) =>
      southPort({ id: `${taskReferenceName}_[key=${idx}]` }, idx),
    ),
    data: {
      task,
      crumbs,
      ...extractExecutionDataOrEmpty(task),
    },
    ...taskToSize(task),
  };
};

export const taskToForkJoinNodesEdges = async (
  task: ForkJoinTaskDef,
  crumbs: Crumb[],
  taskWalkerFn: any,
) => {
  const forkJoinNode = forkJoinTaskToNode(task, crumbs);
  const { nodes: forkJoinInnerNodes, edges: forkJoinInnerEdges } =
    await processForkJoinTasks(task, crumbs, taskWalkerFn);

  const initialElement: NodeData<NodeTaskData>[] = [forkJoinNode];
  return {
    nodes: initialElement.concat(forkJoinInnerNodes),
    edges: forkJoinInnerEdges,
  };
};

export const isForkJoinPathEmpty = (
  forkIndex: number,
  currentTask: ForkJoinTaskDef,
) =>
  forkIndex && currentTask && currentTask.forkTasks?.[forkIndex]?.length === 0;

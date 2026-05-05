import { processForkJoinTasks, forkJoinTaskToNode } from "./forkJoin";
import { ForkJoinDynamicDef, Crumb } from "types";

export const taskToForkJoinDynamicNodesEdges = async (
  task: ForkJoinDynamicDef,
  crumbs: Crumb[],
  taskWalkerFn: any,
) => {
  const forkJoinDynamicNode = forkJoinTaskToNode(task, crumbs);
  const { nodes: forkJoinInnerNodes, edges: forkJoinInnerEdges } =
    await processForkJoinTasks(task, crumbs, taskWalkerFn);

  return {
    nodes: [forkJoinDynamicNode, ...forkJoinInnerNodes],
    edges: forkJoinInnerEdges,
  };
};

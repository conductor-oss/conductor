import { northPort } from "./ports";
import _first from "lodash/first";
import _last from "lodash/last";
import _identity from "lodash/identity";
import _isNil from "lodash/isNil";
import { taskToNode } from "./common";
import { DoWhileTaskDef, Crumb, CommonTaskDef } from "types";
import { NodeData, EdgeData } from "reaflow";

type DoWhileTaskDefWithMaybeExecutionData = DoWhileTaskDef & {
  executionData?: any;
};

const maybeAddPortsToWhileNodes = (nodes: NodeData[]): NodeData[] => {
  if (nodes.length === 0) {
    return nodes;
  } else if (nodes.length === 1) {
    return nodes.map((n) => ({ ...n, ports: n.ports?.concat(northPort(n)) }));
  }

  const firstNode = _first(nodes)!;
  const lastNode = _last(nodes)!;
  const noHeadNoTail = nodes.slice(1, -1);

  return [
    { ...firstNode, ports: firstNode.ports?.concat(northPort(firstNode)) },
    ...noHeadNoTail,
    lastNode,
  ];
};
type NodesEdgesAndCrumbs = {
  nodes: NodeData[];
  edges: EdgeData[];
  crumbs: Crumb[];
};
export const processDoWhile = async (
  doWhileTask: DoWhileTaskDefWithMaybeExecutionData,
  crumbs: Crumb[],
  taskWalkerFn: any,
): Promise<NodesEdgesAndCrumbs> => {
  const { loopOver, taskReferenceName, executionData } = doWhileTask;

  const loopOverNodesEdges = await taskWalkerFn(loopOver, {
    crumbContext: {
      parent: doWhileTask.taskReferenceName,
    },
    crumbs,
  });

  const nodeMapper: (nodes: NodeData[]) => NodeData[] =
    executionData == null ? maybeAddPortsToWhileNodes : _identity;

  return {
    // TODO Fix when importing the sdk
    nodes: [
      taskToNode(doWhileTask as CommonTaskDef, crumbs) as NodeData,
    ].concat(
      nodeMapper(loopOverNodesEdges!.nodes!).map((t) =>
        _isNil(t.parent)
          ? {
              ...t,
              parent: taskReferenceName,
            }
          : t,
      ),
    ),
    edges: loopOverNodesEdges.edges.map((e: EdgeData) =>
      _isNil(e.parent)
        ? {
            ...e,
            parent: taskReferenceName,
          }
        : e,
    ),
    crumbs: crumbs.concat(loopOverNodesEdges.crumbs),
  };
};

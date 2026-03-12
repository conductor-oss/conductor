import _isUndefined from "lodash/isUndefined";
import _isNil from "lodash/isNil";
import _isEmpty from "lodash/isEmpty";
import _flow from "lodash/flow";
import { SubWorkflowTaskDef, Crumb, TaskDef } from "types";
import { NodeData, EdgeData } from "reaflow";
import { taskToNode } from "./common";
import { logger } from "utils";
import { SubWorkflowFunction } from "./types";

const reconfigureNodePorts =
  (idSuffix: string) =>
  ({ ports, ...values }: NodeData) => ({
    ...values,
    ports: _isUndefined(ports)
      ? undefined
      : ports.map((p) => ({ ...p, id: `${p.id}${idSuffix}`, hidden: true })), // Get rid of ports
  });

const ifNotSetParentSetParent = (parent: string) => (t: NodeData) =>
  _isNil(t.parent)
    ? {
        ...t,
        parent,
      }
    : t;

const updateId =
  (idSuffix: string) =>
  ({ id, ...rest }: NodeData) => ({
    ...rest,
    id: `${id}${idSuffix}`,
    ...(_isNil(rest.parent) ? {} : { parent: `${rest.parent}${idSuffix}` }),
  });

const setReadOnly = (node: NodeData) => ({
  ...node,
  ...{ data: { ...node.data, withinExpandedSubWorkflow: true } },
});

const reconfigureEdgePorts =
  (idSuffix: string) =>
  ({ fromPort, toPort, from, to, ...edgeProps }: EdgeData) => ({
    ...edgeProps,
    to: `${to}${idSuffix}`,
    from: `${from}${idSuffix}`,
    fromPort: `${fromPort}${idSuffix}`,
    toPort: `${toPort}${idSuffix}`,
  });

export const processSubWorkflow = async (
  subWorkflowTask: SubWorkflowTaskDef,
  crumbs: Crumb[],
  taskWalkerFn: any,
  subWorkflowFetcher: SubWorkflowFunction,
) => {
  const randSuffix = Math.random().toString(36).substring(2, 7);
  const {
    subWorkflowParam: { name, version },
    taskReferenceName,
  } = subWorkflowTask;

  const subWorkflowNode = [
    taskToNode(subWorkflowTask as unknown as TaskDef, crumbs),
  ];
  if (!_isEmpty(name)) {
    try {
      const subWorkflow = await subWorkflowFetcher(name, version);

      const subWorkflowNodeEdges = await taskWalkerFn(subWorkflow.tasks, {
        crumbContext: {
          parent: subWorkflowTask.taskReferenceName,
        },
        crumbs,
        expandSubWorkflow: false,
        readOnly: true,
      });
      const idSuffix = `_swt_${name}_${randSuffix}`;
      const nodePortsMapper = reconfigureNodePorts(idSuffix);
      const parentSetter = ifNotSetParentSetParent(taskReferenceName);
      const idUpdater = updateId(idSuffix);
      const edgePortMapper = reconfigureEdgePorts(idSuffix);

      const wfNodesEdges = {
        // TODO fix when using sdk
        nodes: subWorkflowNode.concat(
          subWorkflowNodeEdges.nodes.map(
            _flow([nodePortsMapper, idUpdater, parentSetter, setReadOnly]),
          ),
        ),
        edges: subWorkflowNodeEdges.edges.map(
          _flow([nodePortsMapper, idUpdater, edgePortMapper, parentSetter]),
        ),
        crumbs: crumbs.concat(subWorkflowNodeEdges.crumbs),
      };

      return wfNodesEdges;
    } catch (err) {
      logger.error("Error when using subworkflow fetcher ", err);
    }
  }
  return {
    nodes: subWorkflowNode,
    edges: [],
  };
};

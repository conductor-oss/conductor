import { useSelector } from "@xstate/react";
import { ActorRef } from "xstate";
import { useCallback } from "react";
import { ElkRoot, EdgeData, NodeData } from "reaflow";
import {
  selectSelectedNode,
  selectNodes,
  selectEdges,
  selectIsOpenedEdge,
  selectOpenedNode,
  selectWorkflowDefinition,
  selectSelectedEdge,
} from "./selectors";
import { FlowActionTypes, DraggedNodeData } from "./types";
import { WorkflowDef } from "types/WorkflowDef";

export const useFlowMachine = (flowActor: ActorRef<any, any>) => {
  const send = flowActor.send;

  const selectNode = useCallback(
    (node: NodeData) =>
      send({
        type: FlowActionTypes.SELECT_NODE_EVT,
        node,
      }),
    [send],
  );

  const selectTaskWithTaskRef = useCallback(
    (node: NodeData, exactTaskRef: string) =>
      send({
        type: FlowActionTypes.SELECT_TASK_WITH_TASK_REF,
        node,
        exactTaskRef,
      }),
    [send],
  );

  const selectEdge = ({ edge }: { edge: EdgeData }) =>
    send({
      type: FlowActionTypes.SELECT_EDGE_EVT,
      edge,
    });

  const toggleEdgeMenu = useCallback(
    (edge: EdgeData) =>
      send({
        type: FlowActionTypes.OPEN_EDGE_MENU_EVT,
        edge,
      }),
    [send],
  );

  const toggleNodeMenu = useCallback(
    (node: NodeData) =>
      send({
        type: FlowActionTypes.OPEN_NODE_MENU_EVT,
        node,
      }),
    [send],
  );

  const updateWorkflowDefinition = useCallback(
    (workflow: WorkflowDef) =>
      send({
        type: FlowActionTypes.UPDATE_WF_DEFINITION_EVT,
        workflow,
      }),
    [send],
  );

  const handleSetLayout = (layout: ElkRoot) => {
    send({ type: FlowActionTypes.SET_LAYOUT, layout });
  };

  const draggingNodeStarts = (nodeData: DraggedNodeData) => {
    send({ type: FlowActionTypes.DRAG_TASK_BEGIN, nodeData });
  };

  const draggingNodeEnds = (
    fromData: DraggedNodeData,
    toData: DraggedNodeData,
  ) => {
    send({ type: FlowActionTypes.DRAG_TASK_END, fromData, toData });
  };

  const selectedNode = useSelector(flowActor, selectSelectedNode);
  const selectedEdge = useSelector(flowActor, selectSelectedEdge);
  const nodes = useSelector(flowActor, selectNodes);
  const edges = useSelector(flowActor, selectEdges);
  const openedEdge = useSelector(flowActor, selectIsOpenedEdge);
  const openedNode = useSelector(flowActor, selectOpenedNode);
  const workflowDefinition = useSelector(flowActor, selectWorkflowDefinition);
  const panAndZoomActor = useSelector(
    flowActor,
    (state) => state.children?.panAndZoomMachine,
  );
  const isInconsistent = useSelector(flowActor, (state) =>
    state.matches({
      init: {
        diagramRenderer: "inconsistent",
      },
    }),
  );

  const isShowDescription = useSelector(flowActor, (state) =>
    state.hasTag("showDescription"),
  );

  return [
    {
      toggleEdgeMenu,
      selectNode,
      selectEdge,
      toggleNodeMenu,
      updateWorkflowDefinition,
      draggingStarts: draggingNodeStarts,
      draggingNodeEnds,
      handleSetLayout,
      selectTaskWithTaskRef,
    },
    {
      selectedNode,
      selectedEdge,
      nodes,
      edges,
      openedEdge,
      openedNode,
      isInconsistent,
      workflowDefinition,
      panAndZoomActor,
      isShowDescription,
    },
  ] as const;
};

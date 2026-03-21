import { useDraggable, useDroppable } from "@dnd-kit/core";
import { useSelector } from "@xstate/react";
import {
  PanAndZoomContext,
  PanAndZoomMachineContext,
  PanAndZoomStates,
} from "components/flow/components/graphs/PanAndZoomWrapper/state";
import {
  NodeTaskData,
  isSubWorkflowChild,
  isTaskNext,
  isTaskReferenceNestedInTaskReference,
  previousTaskCrumb,
} from "components/flow/nodes/mapper";
import {
  DropPosition,
  FlowContext,
  FlowMachineStates,
} from "components/flow/state";
import fastDeepEqual from "fast-deep-equal";
import { useContext, useMemo } from "react";
import { CommonTaskDef, TaskType } from "types";
import type { State } from "xstate";
import { FlowActorContext } from "../state/FlowActorContext";

interface DragDropNodeProps {
  nodeData: NodeTaskData & { selected?: boolean };
  width?: number;
  height?: number;
  nodeId: string;
}

const useIsPanEnabled = () => {
  const { panAndZoomActor } = useContext(PanAndZoomContext);
  const panIsEnabled = useSelector(
    panAndZoomActor!,
    (state: State<PanAndZoomMachineContext>) =>
      state.matches([
        PanAndZoomStates.IDLE,
        PanAndZoomStates.PAN,
        PanAndZoomStates.PAN_ENABLED,
      ]),
  );
  return panIsEnabled;
};

const useFlowContext = () => {
  // Make this two seperate hooks
  const { flowActor } = useContext(FlowActorContext);
  const draggedNodeData = useSelector(
    flowActor!,
    (state: State<FlowContext>) => state.context.draggedNodeData,
  );
  const canDrag = useSelector(flowActor!, (state: State<FlowContext>) =>
    state.matches([
      [
        FlowMachineStates.INIT,
        FlowMachineStates.DIAGRAM_RENDERER,
        FlowMachineStates.DIAGRAM_RENDERER_INIT,
        FlowMachineStates.DIAGRAM_RENDERER_MENU_CLOSED,
      ],
    ]),
  );
  return { draggedNodeData, canDrag };
};

const DRAG_RESTRICTED_TASKS = [TaskType.SWITCH_JOIN, TaskType.TERMINAL];

const isNodeDataAJoinAfterAFork = (nodeData?: NodeTaskData): boolean => {
  if (nodeData?.task.type === TaskType.JOIN) {
    const previousCrumb = previousTaskCrumb(
      nodeData.crumbs,
      nodeData.task.taskReferenceName,
    );
    return (
      previousCrumb !== undefined &&
      [TaskType.FORK_JOIN, TaskType.FORK_JOIN_DYNAMIC].includes(
        previousCrumb.type,
      )
    );
  }
  return false;
};

export const useDraggableNode = ({
  nodeData,
  width,
  height,
  nodeId,
}: DragDropNodeProps): {
  draggableResult: ReturnType<typeof useDraggable>;
  dragIsDisabled: boolean;
} => {
  const panIsEnabled = useIsPanEnabled();
  const { canDrag } = useFlowContext();
  const dragIsDisabled = useMemo(() => {
    // Determine if its execution by looking at the task data
    const isExecution = nodeData?.task?.executionData != null;
    return (
      isExecution ||
      panIsEnabled ||
      canDrag ||
      DRAG_RESTRICTED_TASKS.includes(nodeData?.task?.type) ||
      isNodeDataAJoinAfterAFork(nodeData) ||
      isSubWorkflowChild(nodeData?.crumbs, nodeData?.task?.taskReferenceName)
    );
  }, [panIsEnabled, nodeData, canDrag]);

  const draggableResult = useDraggable({
    id:
      nodeData.task.type === TaskType.SWITCH_JOIN
        ? `${nodeId}_switch_join`
        : nodeId,
    data: {
      ...nodeData,
      height,
      width,
    },
    disabled: dragIsDisabled,
  });
  return { draggableResult, dragIsDisabled };
};

const isJoinAfterFork = (
  nodeData: NodeTaskData,
  draggedTask?: CommonTaskDef,
) => {
  if (draggedTask == null) return false;
  if (
    [TaskType.FORK_JOIN, TaskType.FORK_JOIN_DYNAMIC].includes(
      draggedTask.type,
    ) &&
    nodeData.task.type === TaskType.JOIN
  ) {
    return isTaskNext(
      nodeData.crumbs,
      draggedTask.taskReferenceName,
      nodeData.task.taskReferenceName,
    );
  }
  return false;
};

export const useDroppableNode = ({
  nodeData,
  position,
  nodeId,
}: DragDropNodeProps & DropPosition) => {
  const panIsEnabled = useIsPanEnabled();
  const { draggedNodeData } = useFlowContext();
  const dropIsDisabled = useMemo(() => {
    const targetTaskReferenceName =
      nodeData.task.type === TaskType.SWITCH_JOIN &&
      nodeData.originalTask?.taskReferenceName
        ? nodeData.originalTask?.taskReferenceName
        : nodeData.task.taskReferenceName;

    if (
      panIsEnabled ||
      isJoinAfterFork(nodeData, draggedNodeData?.task) ||
      (draggedNodeData != null &&
        isTaskReferenceNestedInTaskReference(
          nodeData.crumbs,
          targetTaskReferenceName,
          draggedNodeData.task.taskReferenceName,
        )) ||
      fastDeepEqual(nodeData.crumbs, draggedNodeData?.crumbs)
    ) {
      return true;
    }
    return false;
  }, [panIsEnabled, draggedNodeData, nodeData]);

  const droppableResult = useDroppable({
    id: nodeId,
    data: { ...nodeData, position },
    disabled: dropIsDisabled,
  });

  return { droppableResult, draggedNodeData, dropIsDisabled };
};

import { DndContext, MouseSensor, useSensor, useSensors } from "@dnd-kit/core";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import { isForkJoinPathEmpty } from "components/features/flow/nodes/mapper/forkJoin";
import { isSwitchPathEmpty } from "components/features/flow/nodes/mapper/switch";
import { getFlowTheme } from "components/features/flow/theme";
import { WorkflowEditContext } from "pages/definition/state";
import { buildDataForRemoveBranchOperation } from "pages/definition/state/taskModifier/taskModifier";
import { usePerformOperationOnDefinition } from "pages/definition/state/usePerformOperationOnDefintion";
import { ExecutionActionTypes } from "pages/execution/state";
import {
  FunctionComponent,
  MouseEvent,
  useCallback,
  useContext,
  useRef,
  useState,
} from "react";
import { Canvas, Edge, EdgeData, NodeData, PortData } from "reaflow";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { TaskStatus, TaskType } from "types";
import { ActorRef } from "xstate";
import { CustomLabel, CustomNode } from "./components/graphs";
import PanAndZoomWrapper, {
  usePanAndZoomActor,
} from "./components/graphs/PanAndZoomWrapper";
import { EDGE_SPACING } from "./components/graphs/PanAndZoomWrapper/constants";
import QuickAddMenu from "./components/RichAddTaskMenu/QuickAddMenu";
import { DraggableOverlay, useNodeCollisionDetection } from "./dragDrop";
import {
  DraggedNodeData,
  FlowEvents,
  FlowMachineContextProvider,
  useFlowMachine,
} from "./state";
import { useSelector } from "@xstate/react";

import "./ReaflowOverrides.scss";

interface FlowProps {
  flowActor: ActorRef<FlowEvents>;
  readOnly?: boolean;
  leftPanelExpanded: boolean;
  isExecutionView?: boolean;
}

const dashedEdgeStyles = {
  stroke: "#b1b1b7",
  strokeDasharray: "5",
  strokeDashoffset: 10,
  strokeWidth: 2,
  markerEnd: "none",
};

export const Flow: FunctionComponent<FlowProps> = ({
  flowActor,
  readOnly = false,
  leftPanelExpanded,
  isExecutionView = false,
}) => {
  const mouseSensor = useSensor(MouseSensor, {});
  const sensors = useSensors(mouseSensor);

  const { mode } = useContext(ColorModeContext);
  const theme = getFlowTheme(mode);

  const [
    {
      selectNode,
      toggleEdgeMenu,
      toggleNodeMenu,
      handleSetLayout,
      selectEdge,
      draggingStarts,
      draggingNodeEnds,
    },
    {
      nodes,
      edges,
      openedEdge,
      selectedNode,
      isInconsistent,
      panAndZoomActor,
      isShowDescription,
    },
  ] = useFlowMachine(flowActor);

  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const { handleRemoveTask: onRemoveTask, handleRemoveBranch: onRemoveBranch } =
    usePerformOperationOnDefinition(workflowDefinitionActor!);

  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const [showConfirmDeletePathDialog, setShowConfirmDeletePathDialog] =
    useState(false);
  const edgeAnchorEl = useRef<HTMLElement | null>(null);
  const nodeAnchorEl = useRef<HTMLElement | null>(null);
  const canvasRef = useRef(null);

  const [selectedOperationContext, setSelectedOperationContext] =
    useState<any>(null);

  const onNodeClick = useCallback(
    (event: MouseEvent<HTMLElement>, node: NodeData) => {
      const { target } = event;
      const targetElement = target as HTMLElement;

      if (!isInconsistent) {
        const className = targetElement.className;
        if (className.includes("DeleteButton")) {
          event.preventDefault();
          nodeAnchorEl.current = targetElement;
          setShowConfirmDialog(true);
        } else {
          event.stopPropagation();
        }
        selectNode(node);
      }
    },
    [selectNode, isInconsistent],
  );

  const onToggleMenuClick = useCallback(
    (event: MouseEvent<HTMLElement>, edge: EdgeData) => {
      edgeAnchorEl.current = event.target as HTMLElement;
      toggleEdgeMenu(edge);
      event.stopPropagation();
    },
    [toggleEdgeMenu],
  );
  const collisionDetection = useNodeCollisionDetection(panAndZoomActor!);

  const [
    { notifiedEventType, viewportSize },
    { handleSetEventType, handleCenterOnSelectedTask },
  ] = usePanAndZoomActor(panAndZoomActor);

  const richAddTaskMenuActor = (flowActor as any)?.children?.get(
    "richAddTaskMenuMachine",
  );

  const operationContext = useSelector(
    richAddTaskMenuActor || flowActor,
    (state: { context: { operationContext?: any } }) =>
      richAddTaskMenuActor ? state.context.operationContext : undefined,
  );

  return (
    <FlowMachineContextProvider flowActor={flowActor}>
      {!readOnly && (
        <>
          {showConfirmDialog && (
            <ConfirmChoiceDialog
              handleConfirmationValue={(confirmed) => {
                if (
                  confirmed &&
                  selectedNode?.data?.task != null &&
                  selectedNode?.data?.crumbs != null
                ) {
                  onRemoveTask(selectedNode.data);
                }
                setShowConfirmDialog(false);
              }}
              message={"Are you sure you want to delete this task?"}
            />
          )}
          {showConfirmDeletePathDialog && (
            <ConfirmChoiceDialog
              handleConfirmationValue={(confirmed) => {
                if (confirmed && selectedOperationContext) {
                  onRemoveBranch(selectedOperationContext);
                }

                setShowConfirmDeletePathDialog(false);
                setSelectedOperationContext(null);
              }}
              message={
                <>
                  Are you sure you want to delete the path&nbsp;
                  <strong>{selectedOperationContext?.branchName}</strong> ?
                </>
              }
            />
          )}
          {openedEdge ? (
            <QuickAddMenu
              anchorEl={edgeAnchorEl.current}
              richAddTaskMenuActor={(flowActor as any)?.children.get(
                "richAddTaskMenuMachine",
              )}
            />
          ) : null}
        </>
      )}
      {panAndZoomActor && (
        <DndContext
          onDragEnd={(event) => {
            draggingNodeEnds(
              event?.active?.data?.current as DraggedNodeData,
              event?.over?.data?.current as DraggedNodeData,
            );
          }}
          onDragStart={(event) => {
            if (event?.active?.data?.current) {
              draggingStarts(event?.active?.data?.current as DraggedNodeData);
            }
          }}
          sensors={sensors}
          collisionDetection={collisionDetection}
        >
          <PanAndZoomWrapper
            {...{
              leftPanelExpanded,
              panAndZoomActor,
              isInconsistent,
            }}
            viewPortChildren={<DraggableOverlay flowActor={flowActor} />}
            flowActor={flowActor}
            isExecutionView={isExecutionView}
          >
            <Canvas
              ref={canvasRef}
              nodes={nodes}
              edges={edges}
              animated={true}
              fit={false}
              zoomable={false}
              pannable={false}
              layoutOptions={{
                "org.eclipse.elk.spacing.edgeEdge": EDGE_SPACING.toString(),
                "org.eclipse.elk.padding":
                  "[top=10,left=100,bottom=10,right=100]",
                "org.eclipse.elk.layered.edgeLabels.centerLabelPlacementStrategy":
                  "SPACE_EFFICIENT_LAYER",
                "org.eclipse.elk.nodeLabels.placement": "V_CENTER",
              }}
              edge={(edge: EdgeData) => {
                const edgeStylesForTaskStatus = [
                  TaskStatus.COMPLETED,
                  TaskStatus.COMPLETED_WITH_ERRORS,
                ].includes(edge?.data?.status)
                  ? {
                      stroke: theme.edges.completed.stroke,
                      strokeWidth: theme.edges.completed.strokeWidth,
                    }
                  : {
                      stroke: theme.edges.default.stroke,
                      strokeWidth: theme.edges.default.strokeWidth,
                    };
                const edgeStyles =
                  edge?.data?.unreachableEdge === true ||
                  edge?.data?.delayedEdge === true
                    ? dashedEdgeStyles
                    : edgeStylesForTaskStatus;
                return (
                  <Edge
                    selectable={false}
                    label={
                      <CustomLabel nodes={nodes} selectEdge={selectEdge} />
                    }
                    style={edgeStyles}
                  />
                );
              }}
              node={
                <CustomNode
                  displayDescription={isShowDescription}
                  operationContext={operationContext}
                  onClick={onNodeClick}
                  actions={{ toggleNodeMenu }}
                  onToggleTaskMenu={(
                    event: MouseEvent<HTMLElement>,
                    port: PortData,
                  ) => {
                    onToggleMenuClick(event, port);
                  }}
                  onDeleteBranch={(
                    __event: any,
                    {
                      port,
                      node,
                    }: {
                      port: PortData;
                      node: NodeData;
                    } /* The type is better defined in core modules*/,
                  ) => {
                    const operationContext = buildDataForRemoveBranchOperation({
                      port,
                      node,
                    });

                    if (
                      operationContext?.task?.type === TaskType.SWITCH &&
                      !isSwitchPathEmpty(
                        operationContext?.branchName,
                        operationContext?.task,
                      )
                    ) {
                      setSelectedOperationContext(operationContext);
                      setShowConfirmDeletePathDialog(true);
                    } else if (
                      operationContext?.task?.type === TaskType.FORK_JOIN &&
                      !isForkJoinPathEmpty(
                        operationContext?.branchName,
                        operationContext?.task,
                      )
                    ) {
                      setSelectedOperationContext(operationContext);
                      setShowConfirmDeletePathDialog(true);
                    } else {
                      onRemoveBranch(operationContext);
                    }
                  }}
                  isInconsistent={isInconsistent}
                />
              }
              onLayoutChange={(layout) => {
                if (layout != null && layout.width != null) {
                  handleSetLayout(layout);

                  if (
                    notifiedEventType ===
                    ExecutionActionTypes.COLLAPSE_DYNAMIC_TASK
                  ) {
                    // Reset notified event type
                    handleSetEventType("");

                    // Center selected task
                    handleCenterOnSelectedTask(
                      viewportSize.width,
                      viewportSize.height,
                    );
                  }
                }
              }}
            />
          </PanAndZoomWrapper>
        </DndContext>
      )}
    </FlowMachineContextProvider>
  );
};

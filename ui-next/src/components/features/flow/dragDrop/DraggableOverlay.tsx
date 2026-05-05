import { DragOverlay, useDndContext } from "@dnd-kit/core";
import { useSelector } from "@xstate/react";
import { PanAndZoomMachineContext } from "components/features/flow/components/graphs/PanAndZoomWrapper/state";
import { FlowContext, FlowEvents } from "components/features/flow/state";
import { FunctionComponent, useMemo } from "react";
import { ActorRef, State } from "xstate";
import {
  Shape,
  ShapeComponentForTypeParams,
} from "../components/shapes/TaskShape/Shape";

export interface DragOverlayProps {
  flowActor: ActorRef<FlowEvents>;
}

export const DraggableOverlay: FunctionComponent<DragOverlayProps> = ({
  flowActor,
}) => {
  const { active } = useDndContext();
  const draggedElement = useSelector(
    flowActor,
    (state: State<FlowContext>) => state.context.draggedNodeData,
  );
  // @ts-ignore
  const panAndZoomActor = flowActor.children?.get("panAndZoomMachine");

  return panAndZoomActor ? (
    <DraggableOverlayWithPanZoom
      panAndZoomActor={panAndZoomActor as ActorRef<any>}
      active={!!active}
      draggedElement={draggedElement}
    />
  ) : null;
};

interface DraggableOverlayWithPanZoomProps {
  panAndZoomActor: ActorRef<any>;
  active: boolean;
  draggedElement: any;
}

const DraggableOverlayWithPanZoom: FunctionComponent<
  DraggableOverlayWithPanZoomProps
> = ({ panAndZoomActor, active, draggedElement }) => {
  const scaleFactor = useSelector(
    panAndZoomActor,
    (state: State<PanAndZoomMachineContext>) => state.context.zoom,
  );
  const shapeScaleStyles = useMemo(
    () => ({
      transformOrigin: "top left",
      transform: `scale(${scaleFactor})`,
      opacity: 0.5,
    }),
    [scaleFactor],
  );

  return (
    <DragOverlay>
      {active && draggedElement != null ? (
        <Shape
          nodeData={draggedElement}
          type={draggedElement.task.type as ShapeComponentForTypeParams}
          nodeWidth={draggedElement.width}
          nodeHeight={draggedElement.height}
          isInconsistent={false}
          onToggleTaskMenu={() => {}}
          style={shapeScaleStyles}
        />
      ) : null}
    </DragOverlay>
  );
};

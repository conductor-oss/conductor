import { FunctionComponent, ReactNode } from "react";
import { NodeTaskData } from "components/flow/nodes/mapper";
import { Shape, ShapeComponentForTypeParams } from "./Shape";
import { useDraggableNode } from "components/flow/dragDrop";

interface TaskShapeProps {
  onToggleTaskMenu: (event: any) => void;
  nodeData: NodeTaskData & { selected?: boolean };
  isInconsistent: boolean;
  width?: number;
  height?: number;
  portsVisible?: boolean;
  children?: ReactNode;
  nodeId: string;
  displayDescription?: boolean;
}

export const TaskShape: FunctionComponent<TaskShapeProps> = ({
  onToggleTaskMenu,
  nodeData,
  width = undefined,
  height = undefined,
  portsVisible = false,
  isInconsistent,
  nodeId,
  displayDescription,
}) => {
  const { task } = nodeData;
  const { type } = task;

  const {
    draggableResult: { listeners, setNodeRef },
    dragIsDisabled,
  } = useDraggableNode({
    nodeData,
    width,
    height,
    nodeId,
  });

  return (
    <Shape
      displayDescription={displayDescription}
      handle={!dragIsDisabled}
      listeners={listeners}
      ref={setNodeRef}
      nodeData={nodeData}
      onToggleTaskMenu={onToggleTaskMenu}
      portsVisible={portsVisible}
      nodeHeight={height}
      nodeWidth={width}
      isInconsistent={isInconsistent}
      type={type as ShapeComponentForTypeParams}
      nodeId={nodeId}
    />
  );
};

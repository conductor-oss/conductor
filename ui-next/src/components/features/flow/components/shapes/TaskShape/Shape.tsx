import { DraggableSyntheticListeners } from "@dnd-kit/core";
import { Handle } from "components/features/flow/dragDrop/Handle";
import {
  BOTTOM_PORT_MARGIN,
  NodeTaskData,
} from "components/features/flow/nodes/mapper";
import { CSSProperties, forwardRef, ReactNode, useMemo } from "react";
import { CommonTaskDef, SwitchTaskDef, TaskStatus, TaskType } from "types";
import DecisionOperator from "../DecisionOperator";
import DoWhileTask from "../DoWhileTask";
import DynamicTasksCards from "../DynamicTasksCards";
import SubWorkflowTask from "../SubWorkflowTask";
import SwitchJoin from "../SwitchJoinPseudoTask";
import TaskCard from "../TaskCard/TaskCard";
import TaskSummary from "../TaskSummary";
import TerminalTask from "../TerminalTask";

interface ShapeProps<T extends CommonTaskDef = CommonTaskDef> {
  displayDescription?: boolean;
  type: ShapeComponentForTypeParams;
  nodeData: NodeTaskData<T>;
  onToggleTaskMenu: (event: any) => void;
  portsVisible?: boolean;
  nodeWidth?: number;
  nodeHeight?: number;
  isInconsistent: boolean;
  listeners?: DraggableSyntheticListeners;
  style?: CSSProperties;
  handle?: boolean;
  nodeId?: string;
}
export type ShapeComponentForTypeParams = TaskType & "FORK_JOIN_COLLAPSED";

type ShapePropsToShape<T extends CommonTaskDef = CommonTaskDef> = (
  props: ShapeProps<T>,
) => ReactNode;

const DecisionOperatorShape: ShapePropsToShape<SwitchTaskDef> = (
  props: ShapeProps<SwitchTaskDef>,
) => (
  <DecisionOperator
    nodeData={props.nodeData}
    nodeWidth={props.nodeWidth!}
    portsVisible={!!props?.portsVisible}
    isInconsistent={props.isInconsistent}
    displayDescription={props.displayDescription}
  />
);

const SHAPES_FOR_TYPE = {
  FORK_JOIN_COLLAPSED: (props: ShapeProps) => <DynamicTasksCards {...props} />,
  [TaskType.DO_WHILE]: (props: ShapeProps) => <DoWhileTask {...props} />,
  [TaskType.TERMINAL]: (props: ShapeProps) => (
    <TerminalTask nodeData={props.nodeData} portsVisible={props.portsVisible} />
  ),
  [TaskType.SWITCH]: DecisionOperatorShape,
  [TaskType.DECISION]: DecisionOperatorShape,
  [TaskType.TASK_SUMMARY]: (props: ShapeProps) => <TaskSummary {...props} />,
  [TaskType.SUB_WORKFLOW]: (props: ShapeProps) => (
    <SubWorkflowTask {...props} />
  ),
  [TaskType.SWITCH_JOIN]: (props: ShapeProps) => <SwitchJoin {...props} />,
} satisfies Record<ShapeComponentForTypeParams, ShapePropsToShape>;

export const Shape = forwardRef<HTMLDivElement, ShapeProps>((props, ref) => {
  const {
    type,
    nodeData,
    portsVisible,
    nodeWidth,
    nodeHeight,
    listeners,
    style = {},
    handle = true,
  } = props;
  const dimTask = [TaskStatus.PENDING, TaskStatus.SKIPPED].includes(
    nodeData.status!,
  );
  const containerStyles = useMemo(() => {
    const extraHeight = type === "FORK_JOIN_DYNAMIC" ? 10 : 0;
    const bottomMargin = portsVisible ? BOTTOM_PORT_MARGIN : 0;

    return {
      display: "flex",
      top: 0,
      left: "200px",
      justifyContent: "center",
      opacity: !dimTask ? 1 : 0.75,
      filter: !dimTask ? "" : "grayscale(.75)",
      padding: "0",
      width: nodeWidth || 0,
      height: (nodeHeight || 0) - bottomMargin + extraHeight,
      ...style,
    };
  }, [dimTask, nodeWidth, nodeHeight, type, portsVisible, style]);

  const ShapeComponent: ShapePropsToShape = useMemo(
    () => SHAPES_FOR_TYPE[type] ?? TaskCard,
    [type],
  );

  const handleStyles = useMemo(() => {
    // The Switch Task relies on having extra 100 pixels to space the ports. this positions the handle for that task.
    return [TaskType.DECISION, TaskType.SWITCH].includes(type)
      ? { left: "50px" }
      : {};
  }, [type]);

  return (
    <div
      style={{ ...containerStyles }}
      {...(!handle ? listeners : {})}
      ref={ref}
    >
      {handle ? <Handle {...listeners} style={handleStyles} /> : null}
      <ShapeComponent {...props} />
    </div>
  );
});

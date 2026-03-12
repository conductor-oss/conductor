import { useSelector } from "@xstate/react";
import { usePanAndZoomActor } from "components/flow/components/graphs/PanAndZoomWrapper";
import { FlowActorContext } from "components/flow/state/FlowActorContext";
import Button from "components/MuiButton";
import {
  ExecutionActionTypes,
  FlowExecutionContext,
} from "pages/execution/state";
import { useContext } from "react";

const ForkJoinDynamicTask = ({ nodeData }) => {
  const { onCollapseDynamic } = useContext(FlowExecutionContext);
  const { flowActor } = useContext(FlowActorContext);
  const panAndZoomActor = useSelector(
    flowActor,
    (state) => state.children?.panAndZoomMachine,
  );
  const [, { handleSetEventType }] = usePanAndZoomActor(panAndZoomActor);
  const { collapsed, task } = nodeData;

  return (
    <div style={{ marginTop: "0px" }}>
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          width: "100%",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            width: "100%",
            height: "45px",
          }}
        >
          {collapsed === false && task.executionData?.executed ? (
            <Button
              variant="secondary"
              size="small"
              onClick={() => {
                onCollapseDynamic(task.taskReferenceName);
                handleSetEventType(ExecutionActionTypes.COLLAPSE_DYNAMIC_TASK);
              }}
              style={{
                height: "30px",
                fontSize: "9pt",
                marginTop: "15px",
              }}
            >
              Collapse
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  );
};

export default ForkJoinDynamicTask;

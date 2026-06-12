import { FlowContext, StoppedDraggingNodeEvent } from "./types";

export const hasValidActiveAndCurrent = (
  __context: FlowContext,
  event: StoppedDraggingNodeEvent,
) => {
  const { fromData, toData } = event;
  if (fromData === undefined || toData === undefined) {
    return false;
  }
  if (fromData?.task?.taskReferenceName === toData?.task?.taskReferenceName) {
    return false;
  }
  return true;
};

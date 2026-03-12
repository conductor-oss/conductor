import { WorkflowMetadataMachineContext, WorkflowChangedEvent } from "./types";
import { extractWorkflowMetadata } from "../../helpers";
import fastDeepEqual from "fast-deep-equal";

export const hasMetadataChanges = (
  { metadataChanges }: WorkflowMetadataMachineContext,
  { workflow }: WorkflowChangedEvent,
) => {
  const sliceOfInterest = extractWorkflowMetadata(workflow);
  const hasChanges = fastDeepEqual(sliceOfInterest, metadataChanges);
  return !hasChanges;
};

import { logger } from "utils";
import { DoneInvokeEvent } from "xstate";
import { SaveWorkflowMachineContext } from "./types";

const maybeWorkflowName = (workflowAsString: string): string | null => {
  try {
    const wf = JSON.parse(workflowAsString);
    const { name = null } = wf;
    return name;
  } catch {
    logger.debug("Editor changes is not parsable");
  }
  return null;
};
const maybeWorkflowVersion = (workflowAsString: string): number | null => {
  try {
    const wf = JSON.parse(workflowAsString);
    const { version = null } = wf;
    return version;
  } catch {
    logger.debug("Editor changes is not parsable");
  }
  return null;
};

export const isNewOrNameChanged = ({
  isNewWorkflow,
  currentWf,
  editorChanges,
}: SaveWorkflowMachineContext) =>
  isNewWorkflow ||
  maybeWorkflowName(editorChanges) !== currentWf.name ||
  maybeWorkflowVersion(editorChanges) !== currentWf.version;

export const returnedConflict = (
  _context: SaveWorkflowMachineContext,
  { data }: DoneInvokeEvent<{ status: number }>,
) => data.status === 409;

export const isNewVersion = (_context: SaveWorkflowMachineContext) => {
  return _context.isNewVersion;
};

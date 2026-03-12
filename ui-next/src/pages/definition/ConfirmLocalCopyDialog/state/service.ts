import fastDeepEqual from "fast-deep-equal";
import _isNil from "lodash/isNil";
import {
  extractKeyFromContext,
  removeCopyFromStorage,
} from "pages/runWorkflow/runWorkflowUtils";
import { WorkflowDef } from "types/WorkflowDef";
import { logger } from "utils";

export { removeCopyFromStorage };

const recoverVersionIfWorthIt = (
  wfKey: string,
  savedVersion: string,
  currentWf: { updateTime: number },
): Promise<Partial<WorkflowDef> | null> => {
  try {
    logger.log("Recovered version from Local Storage ", wfKey);
    const savedVersionDef = JSON.parse(savedVersion);

    const isJsonEqual = fastDeepEqual(savedVersionDef, currentWf);
    if (!isJsonEqual) {
      return Promise.resolve(savedVersionDef);
    }
  } catch {
    logger.log("Version is not parsable", wfKey);
  }

  logger.log("Version is not relevant removing", wfKey);
  localStorage.removeItem(wfKey);
  return Promise.reject(null);
};

const isNewWorkflowWorthIt = (
  wfKey: string,
  savedVersion: string,
  currentWf: WorkflowDef,
): Promise<Partial<WorkflowDef> | null> => {
  try {
    const savedVersionDef = JSON.parse(savedVersion);
    const { name: _savedVersionName, ...restOfSavedVersion } = savedVersionDef;

    const { name: _currentVersionName, ...restOfCurrentVersion } = currentWf;
    const isJsonEqual = fastDeepEqual(restOfCurrentVersion, restOfSavedVersion);
    logger.log("Fast Deep Equals says json is Equal ", isJsonEqual);
    if (!isJsonEqual) {
      return Promise.resolve(savedVersionDef);
    }
  } catch {
    logger.log("Could not parse the saved json.");
  }

  logger.log("Discarding localStorage version");
  localStorage.removeItem(wfKey);
  return Promise.reject(null);
};

export const consumeCopyFromLocalStorage = (
  context: any,
): Promise<Partial<WorkflowDef> | null> => {
  const { currentWf, isNewWorkflow } = context;
  const wfKey = extractKeyFromContext(context);
  const savedVersion = localStorage.getItem(wfKey);
  if (!_isNil(savedVersion)) {
    return isNewWorkflow
      ? isNewWorkflowWorthIt(wfKey, savedVersion, currentWf)
      : recoverVersionIfWorthIt(wfKey, savedVersion, currentWf);
  }

  return Promise.reject(null);
};

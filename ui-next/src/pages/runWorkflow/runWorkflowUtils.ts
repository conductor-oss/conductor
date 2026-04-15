import { logger } from "utils/logger";
import { tryToJson } from "utils/utils";
import _isArray from "lodash/isArray";
import {
  DeletedWfNameType,
  DeletedWfVersionType,
  ParsedSelectedWorkflowType,
} from "./types";

export const removeCopyFromStorage = (context: any): Promise<boolean> => {
  removeCachedChangesFromWorkflow(
    context.workflowName,
    context.currentVersion,
    context.isNewWorkflow,
    context.currentWf?.version,
  );

  return Promise.resolve(true);
};

const isNotAValidVersion = (deletedwfversion: DeletedWfVersionType) => {
  return deletedwfversion === null || isNaN(deletedwfversion as number);
};

const cleanupHistoryInLocalStorage = (
  deletedwfname: DeletedWfNameType,
  deletedwfversion: DeletedWfVersionType,
) => {
  const history = localStorage.getItem("workflowHistory");
  const parsedHistory = tryToJson(history);
  if (_isArray(parsedHistory)) {
    const filteredhistory = parsedHistory.filter(
      (item: { name: string; version: string }) => {
        const isDeletedWorkflow =
          item.name === deletedwfname &&
          ((isNotAValidVersion(deletedwfversion) && item.version === null) ||
            parseInt(item.version) === deletedwfversion);
        return !isDeletedWorkflow;
      },
    );
    try {
      localStorage.setItem("workflowHistory", JSON.stringify(filteredhistory));
    } catch (error) {
      logger.error("Error stringifying filteredhistory:", error);
    }
  }
};

const removeSelectedWfInLocalStorage = (deletedWfName: DeletedWfNameType) => {
  const selectedWorkflow = localStorage.getItem("selectedWorkflow");
  const parsedSelectedWorkflow =
    tryToJson<ParsedSelectedWorkflowType>(selectedWorkflow);
  if (
    !!parsedSelectedWorkflow &&
    parsedSelectedWorkflow.name === deletedWfName
  ) {
    localStorage.removeItem("selectedWorkflow");
  }
};

export const extractKeyFromContext = ({
  workflowName,
  currentVersion,
  isNewWorkflow = false,
}: {
  workflowName: string;
  currentVersion?: number;
  isNewWorkflow?: boolean;
}) => {
  return isNewWorkflow
    ? "newWorkflowDef"
    : `${workflowName}/${currentVersion ?? ""}`;
};

const localcopytimekey = "_localcopyupdatedtime";

export const addLocalCopyTime = (wfKey: any) => {
  localStorage.setItem(
    wfKey + localcopytimekey,
    new Date().toLocaleString("en-US"),
  );
};
export const removeLocalCopyTime = (wfKey: any) => {
  localStorage.removeItem(wfKey + localcopytimekey);
};
export const getLocalCopyTime = (wfKey: any) => {
  return localStorage.getItem(wfKey + localcopytimekey);
};

export const removeCachedChangesFromWorkflow = (
  deletedWfName: DeletedWfNameType,
  deletedWfVersion?: DeletedWfVersionType,
  isNewWorkflow = false,
  previousVersion?: DeletedWfVersionType,
) => {
  if (deletedWfName != null) {
    const context = {
      workflowName: deletedWfName,
      currentVersion: deletedWfVersion,
      isNewWorkflow,
    };
    const wfKey = extractKeyFromContext(context);
    localStorage.removeItem(wfKey);

    const wfKeyLastVersion = extractKeyFromContext({
      ...context,
      currentVersion: undefined,
    });
    localStorage.removeItem(wfKeyLastVersion);

    if (previousVersion) {
      const wfKeyPreviousVersion = extractKeyFromContext({
        ...context,
        currentVersion: previousVersion,
      });
      localStorage.removeItem(wfKeyPreviousVersion);
      removeLocalCopyTime(wfKeyPreviousVersion);
    }

    removeLocalCopyTime(wfKeyLastVersion);
    removeLocalCopyTime(wfKey);
    logger.log("Removing version from storage", wfKey);
  }
};

export const removeDeletedWorkflow = (
  deletedWfName: DeletedWfNameType,
  deletedWfVersion: DeletedWfVersionType,
  isNewWorkflow = false,
) => {
  cleanupHistoryInLocalStorage(deletedWfName, deletedWfVersion);
  removeSelectedWfInLocalStorage(deletedWfName);
  removeCopyFromStorage({
    workflowName: deletedWfName,
    currentVersion: deletedWfVersion,
    isNewWorkflow,
  });
};

export function getTemplateFromInputParams(inputParamsArray: any) {
  if (!inputParamsArray) {
    return "";
  }
  const input: { [key: string]: string } = {};
  if (Array.isArray(inputParamsArray)) {
    inputParamsArray.forEach((val: any) => {
      input[val] = "";
    });
  }
  return JSON.stringify(input, null, 2);
}

import _nth from "lodash/nth";
import { useMemo } from "react";
import { getTemplateFromInputParams } from "../../runWorkflow/runWorkflowUtils";
import { IObject } from "types/common";

export interface UseWorkflowConfigReturn {
  workflowNames: string[];
  workflowVersions: string[];
  workflowInputTemplate: string;
  setWorkflowType: (workflowType: string) => {
    workflowVersions: string[];
    workflowInputTemplate: string;
  };
  setWorkflowVersion: (
    workflowVersion: string | null,
    workflowType: string | null,
  ) => {
    workflowInputTemplate: string;
  };
}

export function useWorkflowConfig(
  workflowDefByVersions: any,
  currentWorkflowType: string | null,
  currentWorkflowVersions: string[],
  currentWorkflowInputTemplate: string,
): UseWorkflowConfigReturn {
  const workflowNames = useMemo<string[]>(
    () =>
      workflowDefByVersions
        ? Array.from(workflowDefByVersions.get("lookups").keys())
        : [],
    [workflowDefByVersions],
  );

  // Get workflow versions for the current workflow type
  const workflowVersions = useMemo<string[]>(() => {
    if (currentWorkflowType && workflowDefByVersions) {
      const versions = workflowDefByVersions
        .get("lookups")
        .get(currentWorkflowType);
      return versions ? [...versions] : [];
    }
    return currentWorkflowVersions;
  }, [currentWorkflowType, workflowDefByVersions, currentWorkflowVersions]);

  const setWorkflowType = useMemo(
    () => (workflowType: string) => {
      let workflowVersionsVal: string[] = [];
      let def: IObject = {};

      if (workflowType !== null) {
        workflowVersionsVal = workflowDefByVersions
          .get("lookups")
          .get(workflowType);
      }

      if (workflowVersionsVal && workflowVersionsVal.length > 0) {
        const latestVersion = _nth(
          workflowVersionsVal,
          workflowVersionsVal.length - 1,
        );
        if (latestVersion !== null) {
          def = workflowDefByVersions
            .get("values")
            ?.get(workflowType ? workflowType : currentWorkflowType)
            ?.get(latestVersion);
        }
      }

      return {
        workflowVersions: [...workflowVersionsVal],
        workflowInputTemplate: getTemplateFromInputParams(
          def?.["inputParameters"],
        )
          ? getTemplateFromInputParams(def?.["inputParameters"])
          : currentWorkflowInputTemplate,
      };
    },
    [workflowDefByVersions, currentWorkflowType, currentWorkflowInputTemplate],
  );

  const setWorkflowVersion = useMemo(
    () => (workflowVersion: string | null, workflowType: string | null) => {
      let def: IObject = {};

      if (workflowVersion !== null) {
        const latestVersion = _nth(
          currentWorkflowVersions,
          currentWorkflowVersions.length - 1,
        );
        const requiredWorkflowVersion =
          workflowVersion === "Latest version"
            ? latestVersion
            : workflowVersion;
        def = workflowDefByVersions
          .get("values")
          ?.get(workflowType ? workflowType : currentWorkflowType)
          ?.get(requiredWorkflowVersion);
      }

      return {
        workflowInputTemplate: getTemplateFromInputParams(
          def?.["inputParameters"],
        )
          ? getTemplateFromInputParams(def?.["inputParameters"])
          : currentWorkflowInputTemplate,
      };
    },
    [
      workflowDefByVersions,
      currentWorkflowType,
      currentWorkflowVersions,
      currentWorkflowInputTemplate,
    ],
  );

  return {
    workflowNames,
    workflowVersions,
    workflowInputTemplate: currentWorkflowInputTemplate,
    setWorkflowType,
    setWorkflowVersion,
  };
}

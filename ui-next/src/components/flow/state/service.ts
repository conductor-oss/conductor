import { applyNodeSelectionHelpr } from "./helpers";
import { processWorkflow } from "../nodes";
import _property from "lodash/property";
import _filter from "lodash/filter";
import _includes from "lodash/includes";
import { SEVERITY_ERROR } from "pages/definition/state/constants";
import { FlowContext } from "./types";
import { EdgeData, NodeData } from "reaflow";
import { queryClient } from "../../../queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import _isNil from "lodash/isNil";
import { logger } from "utils";
import { AuthHeaders } from "types/common";

const fetchContext = fetchContextNonHook();

const BASE_PATH = `/metadata/workflow/`;

const fetchForWorkflowDefinition = async ({
  workflowName,
  currentVersion,
  authHeaders,
  collapseWorkflowList,
}: {
  workflowName: string;
  currentVersion: string;
  authHeaders: AuthHeaders;
  collapseWorkflowList: string[];
}) => {
  const path = `${BASE_PATH}${workflowName}${
    _isNil(currentVersion) ? "" : `?version=${currentVersion}`
  }`;
  try {
    if (collapseWorkflowList?.includes(workflowName)) {
      const response = await queryClient.fetchQuery(
        [fetchContext.stack, path],
        () => fetchWithContext(path, fetchContext, { headers: authHeaders }),
      );
      return response;
    }
    return { tasks: [] };
  } catch (error) {
    logger.error("Error fetching for workflow definition ", error);
    return Promise.reject({
      message: "Error searching for workflow definition",
    });
  }
};

export const updateWorkflowDefinitionService = async (
  { selectedNodeIdx, authHeaders, collapseWorkflowList }: FlowContext,
  { workflow, showPorts = true, workflowExecutionStatus = "" }: any,
): Promise<
  | {
      nodes: NodeData[];
      edges: EdgeData[];
      currentWf: any;
    }
  | { severity: "error"; text: string }
> => {
  const expandSubWorkflow = showPorts;

  try {
    const { nodes, edges } = await processWorkflow(
      workflow,
      showPorts,
      expandSubWorkflow, // expand subworkflow
      async (workflowName: string, version?: number) =>
        fetchForWorkflowDefinition({
          workflowName,
          currentVersion: String(version),
          authHeaders: authHeaders!,
          collapseWorkflowList: collapseWorkflowList!,
        }),
      workflowExecutionStatus,
    );
    const justTheIds = nodes.map(_property("id"));
    const duplicates = _filter(justTheIds, (value, index, iteratee) =>
      _includes(iteratee, value, index + 1),
    );

    if (duplicates.length === 0) {
      return {
        nodes: applyNodeSelectionHelpr(nodes, selectedNodeIdx),
        edges,
        currentWf: workflow,
      };
    } else {
      return Promise.reject({
        severity: SEVERITY_ERROR,
        text: `You can't repeat taskReferenceName you have the following duplicates ${duplicates.join(
          ",",
        )}`,
      });
    }
  } catch (error) {
    console.error(error);
    return Promise.reject({
      severity: SEVERITY_ERROR,
      text: "Invalid Json can't process sync. Fix the JSON and try again",
    });
  }
};

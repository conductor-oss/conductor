import { fetchWithContext, useFetchContext } from "../plugins/fetch";
import Path from "./path";
import _ from "lodash";

export const useFetchForWorkflowDefinition = () => {
  const fetchContext = useFetchContext();

  const fetchForWorkflowDefinition = async ({
    workflowName,
    currentVersion,
    collapseWorkflowList,
  }) => {
    const path = new Path(
      `/metadata/workflow/${workflowName}${
        _.isNil(currentVersion) ? "" : `?version=${currentVersion}`
      }`
    );

    try {
      if (collapseWorkflowList?.includes(workflowName)) {
        const response = await fetchWithContext(path, fetchContext);
        return response;
      }
      return { tasks: [] };
    } catch (error) {
      return Promise.reject({
        message: "Error fetching for workflow definition",
      });
    }
  };

  function extractSubWorkflowNames(workflow) {
    const subWorkflowNames = [];

    function traverseTasks(tasks) {
      tasks?.forEach((task) => {
        if (task?.type === "SUB_WORKFLOW" && task?.subWorkflowParam?.name) {
          subWorkflowNames.push(task?.subWorkflowParam.name);
        }

        // Recursively check nested structures
        if (task?.decisionCases) {
          Object.values(task?.decisionCases).forEach(traverseTasks);
        }

        if (task?.defaultCase) {
          traverseTasks(task?.defaultCase);
        }

        if (task?.forkTasks) {
          task?.forkTasks.forEach(traverseTasks);
        }

        if (task?.loopOver) {
          traverseTasks(task?.loopOver);
        }
      });
    }

    if (workflow?.tasks) {
      traverseTasks(workflow?.tasks);
    }

    return subWorkflowNames;
  }

  return { fetchForWorkflowDefinition, extractSubWorkflowNames };
};

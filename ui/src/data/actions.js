import { useAction } from "./common";
import Path from "../utils/path";
import { useFetchContext, fetchWithContext } from "../plugins/fetch";
import { useMutation } from "react-query";
import _ from "lodash";

export const useRestartAction = ({ workflowId, onSuccess }) => {
  return useAction(`/workflow/${workflowId}/restart`, "post", { onSuccess });
};

export const useRestartLatestAction = ({ workflowId, onSuccess }) => {
  return useAction(
    `/workflow/${workflowId}/restart?useLatestDefinitions=true`,
    "post",
    { onSuccess }
  );
};

export const useRetryAction = ({ workflowId, onSuccess }) => {
  return useAction(
    `/workflow/${workflowId}/retry?resumeSubworkflowTasks=false`,
    "post",
    { onSuccess }
  );
};

export const useRetryResumeSubworkflowTasksAction = ({
  workflowId,
  onSuccess,
}) => {
  return useAction(
    `/workflow/${workflowId}/retry?resumeSubworkflowTasks=true`,
    "post",
    { onSuccess }
  );
};

export const useTerminateAction = ({ workflowId, onSuccess }) => {
  const fetchContext = useFetchContext();
  return useMutation(
    (mutateParams) => {
      const reason = _.get(mutateParams, "reason");
      const path = new Path(`/workflow/${workflowId}`);
      if (reason) {
        path.search.append("reason", reason);
      }

      return fetchWithContext(path.toString(), fetchContext, {
        method: "delete",
        headers: {
          "Content-Type": "application/json",
        },
      });
    },
    { onSuccess }
  );
};

export const useResumeAction = ({ workflowId, onSuccess }) => {
  return useAction(`/workflow/${workflowId}/resume`, "put", { onSuccess });
};

export const usePauseAction = ({ workflowId, onSuccess }) => {
  return useAction(`/workflow/${workflowId}/pause`, "put", { onSuccess });
};

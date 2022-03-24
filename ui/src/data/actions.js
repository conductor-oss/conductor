import { useAction } from "./common";

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
  return useAction(`/workflow/${workflowId}`, "delete", { onSuccess });
};
export const useResumeAction = ({ workflowId, onSuccess }) => {
  return useAction(`/workflow/${workflowId}/resume`, "put", { onSuccess });
};

export const usePauseAction = ({ workflowId, onSuccess }) => {
  return useAction(`/workflow/${workflowId}/pause`, "put", { onSuccess });
};

import { useAction } from "./common";

export const useBulkPauseAction = ({ onSuccess }) => {
  return useAction("/workflow/bulk/pause", "put", { onSuccess });
};

export const useBulkResumeAction = ({ onSuccess }) => {
  return useAction("/workflow/bulk/resume", "put", { onSuccess });
};

export const useBulkRestartAction = ({ onSuccess }) => {
  return useAction("/workflow/bulk/restart", "post", { onSuccess });
};

export const useBulkRestartLatestAction = ({ onSuccess }) => {
  return useAction("/workflow/bulk/restart?useLatestDefinitions=true", "post", {
    onSuccess,
  });
};

export const useBulkRetryAction = ({ onSuccess }) => {
  return useAction("/workflow/bulk/retry", "post", { onSuccess });
};

export const useBulkTerminateAction = ({ onSuccess }) => {
  return useAction("/workflow/bulk/terminate", "post", { onSuccess });
};

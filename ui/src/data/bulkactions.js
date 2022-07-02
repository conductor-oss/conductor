import { useAction } from "./common";
import Path from "../utils/path";
import { fetchWithContext, useFetchContext } from "../plugins/fetch";
import { useMutation } from "react-query";
import _ from "lodash";

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

export const useBulkTerminateWithReasonAction = (callbacks) => {
  const fetchContext = useFetchContext();

  return useMutation((mutateParams) => {
    const path = new Path("/workflow/bulk/terminate");
    if (mutateParams.reason) {
      path.search.append("reason", mutateParams.reason);
    }

    return fetchWithContext(path, fetchContext, {
      method: "post",
      headers: {
        "Content-Type": "application/json",
      },
      body: _.get(mutateParams, "body"),
    });
  }, callbacks);
};

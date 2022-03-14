export const NEW_TASK_TEMPLATE = {
  name: "",
  description:
    "Edit or extend this sample task. Set the task name to get started",
  retryCount: 3,
  timeoutSeconds: 3600,
  inputKeys: [],
  outputKeys: [],
  timeoutPolicy: "TIME_OUT_WF",
  retryLogic: "FIXED",
  retryDelaySeconds: 60,
  responseTimeoutSeconds: 600,
  rateLimitPerFrequency: 0,
  rateLimitFrequencyInSeconds: 1,
  ownerEmail: "",
};

export function configureMonaco(monaco) {
  // No-op
}

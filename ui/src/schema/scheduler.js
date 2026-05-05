export const NEW_SCHEDULER_TEMPLATE = {
  name: "",
  cronExpression: "0 */5 * * * *",
  zoneId: "UTC",
  paused: false,
  startWorkflowRequest: {
    name: "workflow_name",
    version: 1,
    input: {},
  },
};

export function configureMonaco(monaco) {
  // No-op
}

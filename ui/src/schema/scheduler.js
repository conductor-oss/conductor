export const NEW_SCHEDULER_TEMPLATE = {
  name: "",
  cronSchedules: [
    {
      cronExpression: "0 * * * * *",
      zoneId: "UTC",
    },
  ],
  startWorkflowRequest: {
    name: "",
    version: 1,
    input: {},
  },
  runCatchupScheduleInstances: false,
  paused: false,
};

export function configureMonaco(monaco) {
  // No-op
}

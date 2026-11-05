import { createMachine, interpret } from "xstate";
import { TaskType } from "types/common";
import { persistTaskDefinitions, persistIntegrations } from "./actions";

const apply = (action: any, data: unknown) => {
  const service = interpret(
    createMachine({
      context: {},
      initial: "ready",
      states: { ready: { on: { LOADED: { actions: action } } } },
    }),
  ).start();
  service.send({ type: "LOADED", data });
  const context = service.state.context as any;
  service.stop();
  return context;
};

describe("Connected App picker entries", () => {
  it("hides only the shared CONNECTED_APP worker without discarding task definitions", () => {
    const definitions = [
      { name: TaskType.CONNECTED_APP },
      { name: TaskType.INTEGRATION },
      { name: "my_worker" },
      { name: "CONNECTED_APP_custom" },
    ];
    const context = apply(persistTaskDefinitions, definitions);
    expect(context.workerMenuItems.map((item: any) => item.name)).toEqual([
      TaskType.INTEGRATION,
      "my_worker",
      "CONNECTED_APP_custom",
    ]);
    expect(context.taskDefinitions).toBe(definitions);
    expect(context.isTaskDefFetched).toBe(true);
  });

  it("preserves provider-specific CONNECTED_APP task entries", () => {
    const context = apply(persistIntegrations, {
      supportedIntegrations: [
        { name: "GitHub", type: "plugin:github", category: "CLOUD" },
      ],
      availableIntegrations: [
        { name: "github-production", type: "plugin:github" },
      ],
    });
    expect(context.supportedIntegrations).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          name: "GitHub",
          type: TaskType.CONNECTED_APP,
        }),
      ]),
    );
  });
});

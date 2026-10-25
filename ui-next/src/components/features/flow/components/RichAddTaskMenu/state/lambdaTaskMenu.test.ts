import { createMachine, interpret } from "xstate";
import { TaskType } from "types/common";
import { RichAddTaskMenuMachineContext, TaskDefinition } from "./types";

let pluginRegistry: typeof import("plugins/registry").pluginRegistry;
let getALL_TASKS: typeof import("../supportedTasks").getALL_TASKS;
let persistTaskDefinitions: typeof import("./actions").persistTaskDefinitions;

beforeEach(async () => {
  vi.resetModules();
  ({ pluginRegistry } = await import("plugins/registry"));
  ({ getALL_TASKS } = await import("../supportedTasks"));
  ({ persistTaskDefinitions } = await import("./actions"));
});

const plugin = {
  id: "lambda-menu-regression",
  name: "Lambda menu regression",
  version: "1.0.0",
  taskMenuItems: [
    {
      name: "AWS Lambda",
      description: "Invoke Lambda",
      type: TaskType.AWS_LAMBDA,
      category: "System" as const,
    },
  ],
};

const definitions: TaskDefinition[] = [
  { name: "AWS_LAMBDA", description: "Remote AWS_LAMBDA worker" },
  { name: "customer_lambda_worker", description: "Customer worker" },
  { name: "HTTP", description: "Other registered task" },
];

const loadDefinitions = () => {
  const service = interpret(
    createMachine<RichAddTaskMenuMachineContext>({
      predictableActionArguments: true,
      initial: "ready",
      context: {} as RichAddTaskMenuMachineContext,
      states: {
        ready: { on: { LOAD: { actions: persistTaskDefinitions } } },
      },
    }),
  ).start();
  service.send({ type: "LOAD", data: definitions });
  const context = service.getSnapshot().context;
  service.stop();
  return context;
};

it("offers only the dedicated Lambda entry while retaining all task definitions", () => {
  pluginRegistry.register(plugin);
  const context = loadDefinitions();
  const entries = [...getALL_TASKS(), ...context.workerMenuItems];
  expect(
    entries.filter((item) => item.type === TaskType.AWS_LAMBDA),
  ).toHaveLength(1);
  expect(context.workerMenuItems.map((item) => item.name)).toEqual([
    "customer_lambda_worker",
    "HTTP",
  ]);
  expect(context.taskDefinitions).toEqual(definitions);
});

it("keeps the legacy worker entry when no Lambda template is registered", () => {
  const context = loadDefinitions();
  expect(context.workerMenuItems.map((item) => item.name)).toEqual(
    definitions.map((task) => task.name),
  );
  expect(
    context.workerMenuItems.every((item) => item.type === TaskType.SIMPLE),
  ).toBe(true);
});

it("keeps the legacy worker entry when the Lambda template is hidden", () => {
  pluginRegistry.register({
    ...plugin,
    taskMenuItems: [{ ...plugin.taskMenuItems[0], hidden: true }],
  });
  expect(loadDefinitions().workerMenuItems.map((item) => item.name)).toContain(
    "AWS_LAMBDA",
  );
});

it("does not hide Lambda just because another plugin task is registered", () => {
  pluginRegistry.register({
    ...plugin,
    taskMenuItems: [{ ...plugin.taskMenuItems[0], type: TaskType.HTTP }],
  });
  expect(loadDefinitions().workerMenuItems.map((item) => item.name)).toContain(
    "AWS_LAMBDA",
  );
});

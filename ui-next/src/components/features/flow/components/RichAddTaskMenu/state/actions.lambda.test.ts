import { describe, expect, it } from "vitest";
import { createMachine, DoneInvokeEvent } from "xstate";
import { TaskType } from "types/common";
import { FEATURES } from "utils/flags";
import { persistTaskDefinitions } from "./actions";
import {
  RichAddMenuTabs,
  RichAddTaskMenuMachineContext,
  TaskDefinition,
} from "./types";

const legacyWorker = {
  name: "AWS_LAMBDA",
  description: "Remote AWS_LAMBDA worker",
  ownerEmail: "orkes-workers@apps.orkes.io",
};

const loadTaskDefinitions = (data: TaskDefinition[]) => {
  const machine = createMachine<
    RichAddTaskMenuMachineContext,
    DoneInvokeEvent<TaskDefinition[]>
  >({
    predictableActionArguments: true,
    initial: "ready",
    context: {} as RichAddTaskMenuMachineContext,
    states: {
      ready: {
        on: {
          "done.invoke.taskDefinitions": { actions: persistTaskDefinitions },
        },
      },
    },
  });
  return machine.transition(machine.initialState, {
    type: "done.invoke.taskDefinitions",
    data,
  }).context;
};

describe("legacy Lambda worker in the shared new-task palette", () => {
  it.each([false, true])(
    "excludes only the retired registration when Lambda rollout is %s",
    (enabled) => {
      window.conductor = {
        [FEATURES.CONNECTED_APPS_ENABLED]: enabled,
        [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: enabled,
      };
      const customDefinitions = [
        { ...legacyWorker, name: "MY_WORKER" },
        { ...legacyWorker, description: "Customer Lambda worker" },
        { ...legacyWorker, ownerEmail: "customer@example.com" },
        { name: "AWS_LAMBDA", description: "Remote AWS_LAMBDA worker" },
        { name: "HTTP", description: "Remote HTTP worker" },
      ];
      const definitions = [legacyWorker, ...customDefinitions];
      const context = loadTaskDefinitions(definitions);

      // Quick search, the rich sidebar, and keyboard preselection share this list.
      expect(context.workerMenuItems).toEqual(
        customDefinitions.map(({ name, description }) => ({
          name,
          description,
          category: RichAddMenuTabs.WORKERS_TAB,
          type: TaskType.SIMPLE,
        })),
      );
      // Keep metadata intact for existing definitions; this is not a migration.
      expect(context.taskDefinitions).toBe(definitions);
      expect(definitions).toHaveLength(6);
      expect(context.isTaskDefFetched).toBe(true);
    },
  );

  it("handles an empty task-definition response", () => {
    expect(loadTaskDefinitions([]).workerMenuItems).toEqual([]);
  });
});

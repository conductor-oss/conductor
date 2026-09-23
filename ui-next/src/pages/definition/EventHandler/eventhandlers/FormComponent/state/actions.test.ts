/**
 * Event handler form mutations live in xstate assign actions. Playwright only
 * fills name (+ description) against the default template, so adding/removing
 * actions or editing fields can regress without any E2E failure. These assert
 * the machine context that the Code tab and Save payload come from.
 */
import { interpret } from "xstate";
import { describe, expect, it } from "vitest";
import {
  COMPLETE_TASK_ACTION,
  FAIL_TASK_ACTION,
  NEW_EVENT_HANDLER_TEMPLATE,
  START_AGENT_ACTION,
  START_WORKFLOW_ACTION,
  TERMINATE_WORKFLOW_ACTION,
  UPDATE_VARIABLES_ACTION,
} from "../../eventHandlerSchema";
import { eventFormMachine } from "./machine";
import { Action, EventFormMachineTypes } from "./types";
import { ConductorEvent } from "types/Events";

const startWith = (overrides: Partial<ConductorEvent> = {}) => {
  const eventAsJson = {
    ...NEW_EVENT_HANDLER_TEMPLATE,
    ...overrides,
  };

  return interpret(
    eventFormMachine.withContext({
      eventAsJson,
      originalSource: { ...eventAsJson },
    }),
  ).start();
};

describe("eventFormMachine — field input", () => {
  it("writes name, description, event, condition, and active into context", () => {
    const service = startWith();

    service.send({
      type: EventFormMachineTypes.INPUT_CHANGE,
      name: "name",
      value: "handler_a",
    } as never);
    service.send({
      type: EventFormMachineTypes.INPUT_CHANGE,
      name: "description",
      value: "from unit test",
    } as never);
    service.send({
      type: EventFormMachineTypes.INPUT_CHANGE,
      name: "event",
      value: "conductor:my_event",
    } as never);
    service.send({
      type: EventFormMachineTypes.INPUT_CHANGE,
      name: "condition",
      value: "$.status == 'COMPLETED'",
    } as never);
    service.send({
      type: EventFormMachineTypes.INPUT_CHANGE,
      name: "active",
      value: false,
    } as never);

    expect(service.getSnapshot().context.eventAsJson).toMatchObject({
      name: "handler_a",
      description: "from unit test",
      event: "conductor:my_event",
      condition: "$.status == 'COMPLETED'",
      active: false,
    });

    service.stop();
  });

  it("stores an empty string when value is undefined", () => {
    const service = startWith({ name: "keep_me" });

    service.send({
      type: EventFormMachineTypes.INPUT_CHANGE,
      name: "name",
      value: undefined,
    } as never);

    expect(service.getSnapshot().context.eventAsJson.name).toBe("");
    service.stop();
  });
});

describe("eventFormMachine — actions list", () => {
  const cases: Array<{ type: Action; expected: unknown }> = [
    { type: Action.COMPLETE_TASK, expected: COMPLETE_TASK_ACTION },
    { type: Action.FAIL_TASK, expected: FAIL_TASK_ACTION },
    { type: Action.START_WORKFLOW, expected: START_WORKFLOW_ACTION },
    { type: Action.START_AGENT, expected: START_AGENT_ACTION },
    { type: Action.TERMINATE_WORKFLOW, expected: TERMINATE_WORKFLOW_ACTION },
    {
      type: Action.UPDATE_WORKFLOW_VARIABLES,
      expected: UPDATE_VARIABLES_ACTION,
    },
  ];

  it.each(cases)(
    "prepends a $type template when Add action is sent",
    ({ type, expected }) => {
      const service = startWith({ actions: [] });

      service.send({
        type: EventFormMachineTypes.ADD_ACTION,
        actionType: type,
      } as never);

      expect(service.getSnapshot().context.eventAsJson.actions).toEqual([
        expected,
      ]);
      service.stop();
    },
  );

  it("leaves actions unchanged for an unknown action type", () => {
    const service = startWith({ actions: [COMPLETE_TASK_ACTION] });

    service.send({
      type: EventFormMachineTypes.ADD_ACTION,
      actionType: "not_a_real_action",
    } as never);

    expect(service.getSnapshot().context.eventAsJson.actions).toEqual([
      COMPLETE_TASK_ACTION,
    ]);
    service.stop();
  });

  it("removes an action by index", () => {
    const service = startWith({
      actions: [COMPLETE_TASK_ACTION, FAIL_TASK_ACTION, START_WORKFLOW_ACTION],
    });

    service.send({
      type: EventFormMachineTypes.DELETE_ACTION,
      index: 1,
    } as never);

    expect(service.getSnapshot().context.eventAsJson.actions).toEqual([
      COMPLETE_TASK_ACTION,
      START_WORKFLOW_ACTION,
    ]);
    service.stop();
  });

  it("replaces an action payload at an index", () => {
    const service = startWith({ actions: [COMPLETE_TASK_ACTION] });
    const edited = {
      ...COMPLETE_TASK_ACTION,
      complete_task: {
        workflowId: "wf-123",
        taskRefName: "my_task",
      },
    };

    service.send({
      type: EventFormMachineTypes.EDIT_ACTION,
      index: 0,
      payload: edited,
    } as never);

    expect(service.getSnapshot().context.eventAsJson.actions?.[0]).toEqual(
      edited,
    );
    service.stop();
  });

  it.each([
    {
      from: 0,
      to: 2,
      expected: [FAIL_TASK_ACTION, START_WORKFLOW_ACTION, COMPLETE_TASK_ACTION],
    },
    {
      from: 2,
      to: 0,
      expected: [START_WORKFLOW_ACTION, COMPLETE_TASK_ACTION, FAIL_TASK_ACTION],
    },
  ])("moves an action from $from to $to", ({ from, to, expected }) => {
    const service = startWith({
      actions: [COMPLETE_TASK_ACTION, FAIL_TASK_ACTION, START_WORKFLOW_ACTION],
    });

    service.send({
      type: EventFormMachineTypes.MOVE_ACTION,
      from,
      to,
    } as never);

    expect(service.getSnapshot().context.eventAsJson.actions).toEqual(expected);
    service.stop();
  });
});

describe("eventFormMachine — reset", () => {
  it("restores eventAsJson from originalSource on reset confirm", () => {
    const service = startWith({ name: "original" });

    service.send({
      type: EventFormMachineTypes.INPUT_CHANGE,
      name: "name",
      value: "edited",
    } as never);
    expect(service.getSnapshot().context.eventAsJson.name).toBe("edited");

    service.send({ type: EventFormMachineTypes.RESET_CONFIRM_EVT } as never);

    expect(service.getSnapshot().context.eventAsJson.name).toBe("original");
    service.stop();
  });

  it("resets to the new-handler template on confirm new event", () => {
    const service = startWith({
      name: "something",
      event: "custom:event",
      actions: [],
    });

    service.send({ type: EventFormMachineTypes.CONFIRM_NEW_EVENT } as never);

    expect(service.getSnapshot().context.eventAsJson).toEqual(
      NEW_EVENT_HANDLER_TEMPLATE,
    );
    expect(service.getSnapshot().context.originalSource).toEqual(
      NEW_EVENT_HANDLER_TEMPLATE,
    );
    service.stop();
  });
});

describe("NEW_EVENT_HANDLER_TEMPLATE", () => {
  it("ships a saveable default: event string, condition, and complete_task", () => {
    expect(NEW_EVENT_HANDLER_TEMPLATE).toMatchObject({
      name: "",
      event: expect.stringMatching(/\S/),
      condition: "true",
      evaluatorType: "javascript",
      actions: [
        expect.objectContaining({
          action: "complete_task",
          complete_task: expect.objectContaining({
            workflowId: "${workflowId}",
            taskRefName: "${taskReferenceName}",
          }),
        }),
      ],
    });
  });
});

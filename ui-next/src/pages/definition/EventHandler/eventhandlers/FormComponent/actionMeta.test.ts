/**
 * actionSummary is the only view of an action while its card is collapsed, so
 * every branch of it — per action type, filled and empty — is pinned here.
 * templateFor must hand out independent copies for every type.
 */
import { describe, expect, it } from "vitest";
import { EventHandlerAction } from "types/Events";
import {
  actionMeta,
  actionOrder,
  actionSummary,
  templateFor,
} from "./actionMeta";
import { Action } from "./state/types";

const allActions = Object.values(Action);

describe("actionMeta", () => {
  it("lists every action type exactly once in the menu order", () => {
    expect([...actionOrder].sort()).toEqual([...allActions].sort());
  });

  it.each(allActions)("%s template carries its own discriminant", (type) => {
    expect(actionMeta[type].template.action).toBe(type);
  });
});

describe("templateFor", () => {
  it.each(allActions)("returns a deep copy of the %s template", (type) => {
    const copy = templateFor(type) as any;
    const template = actionMeta[type].template as any;

    expect(copy).toEqual(template);
    expect(copy).not.toBe(template);
    expect(copy[type]).not.toBe(template[type]);

    copy[type].mutated = "yes";
    expect(template[type].mutated).toBeUndefined();
  });

  it.each(allActions)("two %s copies do not share state", (type) => {
    const a = templateFor(type) as any;
    const b = templateFor(type) as any;
    a[type].mutated = "yes";
    expect(b[type].mutated).toBeUndefined();
  });
});

describe("actionSummary", () => {
  const cases: Array<[string, EventHandlerAction, string]> = [
    // complete_task / fail_task: taskId wins over taskRefName, else placeholder
    ...(["complete_task", "fail_task"] as const).flatMap(
      (type): Array<[string, EventHandlerAction, string]> => [
        [`${type} empty`, { action: type, [type]: {} } as any, "no task set"],
        [
          `${type} empty strings`,
          { action: type, [type]: { workflowId: "", taskRefName: "" } } as any,
          "no task set",
        ],
        [
          `${type} task ref only`,
          {
            action: type,
            [type]: { workflowId: "wf", taskRefName: "ref" },
          } as any,
          "ref",
        ],
        [
          `${type} task id only`,
          { action: type, [type]: { taskId: "tid" } } as any,
          "tid",
        ],
        [
          `${type} task id beats ref`,
          {
            action: type,
            [type]: { taskId: "tid", taskRefName: "ref" },
          } as any,
          "tid",
        ],
      ],
    ),
    [
      "terminate_workflow empty",
      {
        action: "terminate_workflow",
        terminate_workflow: { workflowId: "" },
      } as any,
      "no workflow set",
    ],
    [
      "terminate_workflow set",
      {
        action: "terminate_workflow",
        terminate_workflow: { workflowId: "${event.payload.id}" },
      } as any,
      "${event.payload.id}",
    ],
    [
      "update_workflow_variables no variables key",
      {
        action: "update_workflow_variables",
        update_workflow_variables: { workflowId: "wf" },
      } as any,
      "no variables",
    ],
    [
      "update_workflow_variables empty variables",
      {
        action: "update_workflow_variables",
        update_workflow_variables: { workflowId: "wf", variables: {} },
      } as any,
      "no variables",
    ],
    [
      "update_workflow_variables one variable",
      {
        action: "update_workflow_variables",
        update_workflow_variables: { variables: { a: 1 } },
      } as any,
      "a",
    ],
    [
      "update_workflow_variables several variables",
      {
        action: "update_workflow_variables",
        update_workflow_variables: { variables: { a: 1, b: 2, c: 3 } },
      } as any,
      "a, b, c",
    ],
    [
      "start_workflow no name",
      {
        action: "start_workflow",
        start_workflow: { name: "", version: "3" },
      } as any,
      "no workflow set",
    ],
    [
      "start_workflow name, latest",
      {
        action: "start_workflow",
        start_workflow: { name: "wf", version: "" },
      } as any,
      "wf",
    ],
    [
      "start_workflow name, string version",
      {
        action: "start_workflow",
        start_workflow: { name: "wf", version: "2" },
      } as any,
      "wf v2",
    ],
    [
      "start_workflow name, numeric version from server",
      {
        action: "start_workflow",
        start_workflow: { name: "wf", version: 2 },
      } as any,
      "wf v2",
    ],
    [
      "start_agent no name",
      { action: "start_agent", start_agent: { name: "" } } as any,
      "no agent set",
    ],
    [
      "start_agent name",
      {
        action: "start_agent",
        start_agent: { name: "agent", version: "1" },
      } as any,
      "agent",
    ],
  ];

  it.each(cases)("%s", (_label, payload, expected) => {
    expect(actionSummary(payload)).toBe(expected);
  });

  it.each(allActions)("summarises the blank %s template", (type) => {
    expect(actionSummary(templateFor(type))).toMatch(/^no /);
  });
});

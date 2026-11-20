/**
 * Permutations of every action sub-form: each field on its own, the modes a
 * form can be in (task ID vs workflow ID + task ref, Latest vs pinned version,
 * idempotency key with/without strategy), and switching between them. Each
 * test drives a controlled harness, so a field that drops its siblings or
 * leaks state from another mode shows up in the payload the form emits.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { ComponentType, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import {
  COMPLETE_TASK_ACTION,
  FAIL_TASK_ACTION,
  START_AGENT_ACTION,
  START_WORKFLOW_ACTION,
  TERMINATE_WORKFLOW_ACTION,
  UPDATE_VARIABLES_ACTION,
} from "../../eventHandlerSchema";
import { CompleteTask } from "./CompleteTask";
import { FailTask } from "./FailTask";
import { StartAgentActionForm } from "./StartAgentTask";
import { StartWorkflowActionForm } from "./StartWorkflowTask";
import { TerminateWorkflowForm } from "./TerminateWorkflowTask";
import { UpdateWorkflowForm } from "./UpdateWorkflowTask";

const fetched = vi.hoisted(() => ({
  workflows: new Map<string, number[]>(),
  agents: [] as Array<{ name: string; version: number }>,
}));

vi.mock("utils/query", () => ({
  useWorkflowNamesAndVersions: () => fetched.workflows,
  useFetch: () => ({ data: fetched.agents }),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ label, value, multiline, onTextInputChange }: any) => {
    const Tag = multiline ? "textarea" : "input";
    return (
      <Tag
        aria-label={label}
        value={value ?? ""}
        onChange={(event: any) => onTextInputChange?.(event.target.value)}
      />
    );
  },
}));

vi.mock("components/ui/inputs", () => ({
  ConductorAutoComplete: ({ label, value, options, onChange, onBlur }: any) => (
    <input
      aria-label={label}
      data-options={JSON.stringify(options ?? [])}
      value={value ?? ""}
      onChange={(event) => onChange?.(null, event.target.value)}
      onBlur={onBlur}
    />
  ),
}));

vi.mock("components/FlatMapForm/ConductorFlatMapForm", () => ({
  ConductorFlatMapFormBase: ({ title, value, onChange }: any) => (
    <textarea
      aria-label={title}
      value={JSON.stringify(value ?? {})}
      onChange={(event) => onChange(JSON.parse(event.target.value))}
    />
  ),
}));

vi.mock("pages/runWorkflow/IdempotencyForm", () => ({
  default: ({ idempotencyValues, onChange }: any) => (
    <>
      <input
        aria-label="Idempotency key"
        value={idempotencyValues.idempotencyKey ?? ""}
        onChange={(event) =>
          onChange({
            idempotencyKey: event.target.value,
            idempotencyStrategy: idempotencyValues.idempotencyStrategy,
          })
        }
      />
      <select
        aria-label="Idempotency strategy"
        value={idempotencyValues.idempotencyStrategy ?? ""}
        onChange={(event) =>
          onChange({
            idempotencyKey: idempotencyValues.idempotencyKey,
            idempotencyStrategy: event.target.value,
          })
        }
      >
        <option value="">none</option>
        <option value="FAIL">FAIL</option>
        <option value="RETURN_EXISTING">RETURN_EXISTING</option>
        <option value="FAIL_ON_RUNNING">FAIL_ON_RUNNING</option>
      </select>
    </>
  ),
}));

const clone = <T,>(value: T): T => structuredClone(value);

/** Renders `Component` as a controlled form and tracks what it emits. */
const renderAction = (Component: ComponentType<any>, initial: any) => {
  let latest = initial;
  const handleChangeAction = vi.fn();
  const Harness = () => {
    const [payload, setPayload] = useState(initial);
    return (
      <Component
        index={0}
        payload={payload}
        handleChangeAction={(index: number, next: any) => {
          handleChangeAction(index, next);
          latest = next;
          setPayload(next);
        }}
      />
    );
  };
  render(
    <ThemeProvider>
      <Harness />
    </ThemeProvider>,
  );
  return { latest: () => latest, handleChangeAction };
};

const type = (label: string, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } });

const setMap = (label: string, value: Record<string, unknown>) =>
  type(label, JSON.stringify(value));

// ── Complete / Fail task ───────────────────────────────────────────────────────

describe.each([
  {
    key: "complete_task",
    Component: CompleteTask,
    template: COMPLETE_TASK_ACTION,
  },
  { key: "fail_task", Component: FailTask, template: FAIL_TASK_ACTION },
] as const)("$key", ({ key, Component, template }) => {
  const body = (latest: () => any) => latest()[key];

  it("opens in workflow ID + task ref mode for a blank action", () => {
    renderAction(Component, clone(template));

    expect(screen.getByLabelText("Workflow Id + Task Ref Name")).toBeChecked();
    expect(screen.getByLabelText("Workflow ID")).toBeInTheDocument();
    expect(screen.getByLabelText("Task reference name")).toBeInTheDocument();
    expect(screen.queryByLabelText("Task ID")).not.toBeInTheDocument();
  });

  it("opens in task ID mode for a saved task-ID action", () => {
    renderAction(Component, { ...clone(template), [key]: { taskId: "t-1" } });

    expect(screen.getByLabelText("Task Id")).toBeChecked();
    expect(screen.getByLabelText("Task ID")).toHaveValue("t-1");
    expect(screen.queryByLabelText("Workflow ID")).not.toBeInTheDocument();
  });

  it("opens in task ID mode for an empty task ID", () => {
    renderAction(Component, { ...clone(template), [key]: { taskId: "" } });

    expect(screen.getByLabelText("Task Id")).toBeChecked();
  });

  it.each([
    ["Workflow ID", { workflowId: "wf", taskRefName: "" }],
    ["Task reference name", { workflowId: "", taskRefName: "wf" }],
  ])("writes %s on its own", (label, expected) => {
    const { latest } = renderAction(Component, clone(template));

    type(label, "wf");

    expect(body(latest)).toEqual({ ...expected, output: undefined });
    expect(latest().action).toBe(key);
    expect(latest().expandInlineJSON).toBe(false);
  });

  it("writes workflow ID and task ref together", () => {
    const { latest } = renderAction(Component, clone(template));

    type("Workflow ID", "${event.payload.wf}");
    type("Task reference name", "wait_ref");

    expect(body(latest)).toEqual({
      workflowId: "${event.payload.wf}",
      taskRefName: "wait_ref",
      output: undefined,
    });
  });

  it("switching to task ID drops workflow ID and task ref", () => {
    const { latest } = renderAction(Component, {
      ...clone(template),
      [key]: { workflowId: "wf", taskRefName: "ref" },
    });

    fireEvent.click(screen.getByLabelText("Task Id"));

    expect(body(latest)).toEqual({ taskId: "", output: undefined });
    expect(screen.getByLabelText("Task ID")).toBeInTheDocument();
    expect(screen.queryByLabelText("Workflow ID")).not.toBeInTheDocument();
  });

  it("typing a task ID never sends workflow ID or task ref", () => {
    const { latest } = renderAction(Component, clone(template));

    fireEvent.click(screen.getByLabelText("Task Id"));
    type("Task ID", "${event.payload.taskId}");

    expect(body(latest)).toEqual({
      taskId: "${event.payload.taskId}",
      output: undefined,
    });
  });

  it("switching back to workflow ID + task ref drops the task ID", () => {
    const { latest } = renderAction(Component, {
      ...clone(template),
      [key]: { taskId: "t-1" },
    });

    fireEvent.click(screen.getByLabelText("Workflow Id + Task Ref Name"));

    expect(body(latest)).toEqual({
      workflowId: "",
      taskRefName: "",
      output: undefined,
    });
  });

  // The mode switch is lossy by design: the payload only ever holds one way
  // of identifying the task, so the other mode's values do not come back.
  it("a round trip through task ID mode clears the earlier workflow ID", () => {
    const { latest } = renderAction(Component, clone(template));

    type("Workflow ID", "wf");
    fireEvent.click(screen.getByLabelText("Task Id"));
    fireEvent.click(screen.getByLabelText("Workflow Id + Task Ref Name"));

    expect(body(latest).workflowId).toBe("");
  });

  it("writes output without touching the task reference", () => {
    const { latest } = renderAction(Component, {
      ...clone(template),
      [key]: { workflowId: "wf", taskRefName: "ref" },
    });

    setMap("Output", { approvedBy: "e2e", count: 2 });

    expect(body(latest)).toEqual({
      workflowId: "wf",
      taskRefName: "ref",
      output: { approvedBy: "e2e", count: 2 },
    });
  });

  it.each([
    ["to task ID", "Task Id", { workflowId: "wf", taskRefName: "ref" }],
    ["to workflow ID", "Workflow Id + Task Ref Name", { taskId: "t" }],
  ])("keeps output when switching %s", (_label, radio, start) => {
    const { latest } = renderAction(Component, {
      ...clone(template),
      [key]: { ...start, output: { a: 1 } },
    });

    fireEvent.click(screen.getByLabelText(radio));

    expect(body(latest).output).toEqual({ a: 1 });
  });

  it.each([
    [
      "Workflow ID",
      "workflow-id-task-ref",
      { workflowId: "wf", taskRefName: "ref" },
    ],
    ["Task ID", "task-id", { taskId: "t" }],
  ])("keeps output when editing %s", (label, _mode, start) => {
    const { latest } = renderAction(Component, {
      ...clone(template),
      [key]: { ...start, output: { a: 1 } },
    });

    type(label, "changed");

    expect(body(latest).output).toEqual({ a: 1 });
  });

  it("does not mutate the shared template", () => {
    renderAction(Component, template);

    type("Workflow ID", "wf");
    setMap("Output", { a: 1 });

    expect((template as any)[key]).toEqual({
      workflowId: "",
      taskRefName: "",
    });
  });
});

// ── Terminate workflow ─────────────────────────────────────────────────────────

describe("terminate_workflow", () => {
  it.each([
    ["Workflow ID", { workflowId: "wf", terminationReason: "" }],
    ["Termination reason", { workflowId: "", terminationReason: "wf" }],
  ])("writes %s on its own", (label, expected) => {
    const { latest } = renderAction(
      TerminateWorkflowForm,
      clone(TERMINATE_WORKFLOW_ACTION),
    );

    type(label, "wf");

    expect(latest().terminate_workflow).toEqual(expected);
  });

  it("writes both fields", () => {
    const { latest } = renderAction(
      TerminateWorkflowForm,
      clone(TERMINATE_WORKFLOW_ACTION),
    );

    type("Workflow ID", "${event.payload.wf}");
    type("Termination reason", "cancelled");

    expect(latest()).toEqual({
      action: "terminate_workflow",
      expandInlineJSON: false,
      terminate_workflow: {
        workflowId: "${event.payload.wf}",
        terminationReason: "cancelled",
      },
    });
  });

  it("can clear a field back to empty", () => {
    const { latest } = renderAction(TerminateWorkflowForm, {
      ...clone(TERMINATE_WORKFLOW_ACTION),
      terminate_workflow: { workflowId: "wf", terminationReason: "r" },
    });

    type("Termination reason", "");

    expect(latest().terminate_workflow).toEqual({
      workflowId: "wf",
      terminationReason: "",
    });
  });
});

// ── Update workflow variables ──────────────────────────────────────────────────

describe("update_workflow_variables", () => {
  const APPEND = "Append List Variables (instead of replacing)";

  it("starts unchecked when appendArray is absent", () => {
    renderAction(UpdateWorkflowForm, clone(UPDATE_VARIABLES_ACTION));

    expect(screen.getByLabelText(APPEND)).not.toBeChecked();
  });

  it("starts checked for a saved appendArray: true", () => {
    renderAction(UpdateWorkflowForm, {
      ...clone(UPDATE_VARIABLES_ACTION),
      update_workflow_variables: { workflowId: "", appendArray: true },
    });

    expect(screen.getByLabelText(APPEND)).toBeChecked();
  });

  it("toggles appendArray on and off", () => {
    const { latest } = renderAction(
      UpdateWorkflowForm,
      clone(UPDATE_VARIABLES_ACTION),
    );

    fireEvent.click(screen.getByLabelText(APPEND));
    expect(latest().update_workflow_variables.appendArray).toBe(true);

    fireEvent.click(screen.getByLabelText(APPEND));
    expect(latest().update_workflow_variables.appendArray).toBe(false);
  });

  // Each field written last, with the other two already set: none of them
  // may drop a sibling.
  it.each(["workflowId", "appendArray", "variables"] as const)(
    "editing %s keeps the other fields",
    (last) => {
      const { latest } = renderAction(
        UpdateWorkflowForm,
        clone(UPDATE_VARIABLES_ACTION),
      );
      const steps = {
        workflowId: () => type("Workflow ID", "wf"),
        appendArray: () => fireEvent.click(screen.getByLabelText(APPEND)),
        variables: () => setMap("Output", { a: "${event.payload.a}" }),
      };
      (Object.keys(steps) as Array<keyof typeof steps>)
        .filter((step) => step !== last)
        .forEach((step) => steps[step]());
      steps[last]();

      expect(latest().update_workflow_variables).toEqual({
        workflowId: "wf",
        appendArray: true,
        variables: { a: "${event.payload.a}" },
      });
    },
  );

  it("clearing every variable stores an empty map", () => {
    const { latest } = renderAction(UpdateWorkflowForm, {
      ...clone(UPDATE_VARIABLES_ACTION),
      update_workflow_variables: { workflowId: "wf", variables: { a: 1 } },
    });

    setMap("Output", {});

    expect(latest().update_workflow_variables.variables).toEqual({});
  });
});

// ── Start workflow ─────────────────────────────────────────────────────────────

describe("start_workflow", () => {
  const start = (overrides: Record<string, unknown> = {}) =>
    renderAction(StartWorkflowActionForm, {
      ...clone(START_WORKFLOW_ACTION),
      start_workflow: { ...START_WORKFLOW_ACTION.start_workflow, ...overrides },
    });
  const body = (latest: () => any) => latest().start_workflow;
  const options = (label: string) =>
    JSON.parse(screen.getByLabelText(label).getAttribute("data-options")!);

  it.each([
    ["empty string", "", "Latest"],
    ["undefined", undefined, "Latest"],
    ["null", null, "Latest"],
    ["a string version", "3", "3"],
    ["a numeric version from the server", 2, "2"],
  ])("shows version for %s as %s", (_label, version, shown) => {
    start({ name: "wf", version });

    expect(screen.getByLabelText("Workflow version")).toHaveValue(shown);
  });

  it.each([
    ["Latest", ""],
    ["latest", ""],
    ["  LATEST  ", ""],
    ["", ""],
    ["3", "3"],
    ["${event.payload.version}", "${event.payload.version}"],
  ])("stores version %j as %j", (typed, stored) => {
    const { latest } = start({ name: "wf", version: "1" });

    type("Workflow version", typed);

    expect(body(latest).version).toBe(stored);
  });

  it("stores the blurred version text too", () => {
    const { latest } = start({ name: "wf" });

    fireEvent.blur(screen.getByLabelText("Workflow version"), {
      target: { value: "Latest" },
    });

    expect(body(latest).version).toBe("");
  });

  it.each([
    ["typing", (v: string) => type("Workflow name", v)],
    [
      "blurring",
      (v: string) =>
        fireEvent.blur(screen.getByLabelText("Workflow name"), {
          target: { value: v },
        }),
    ],
  ])("a new name resets a pinned version (%s)", (_how, setName) => {
    const { latest } = start({ name: "old_wf", version: "4" });

    setName("new_wf");

    expect(body(latest)).toMatchObject({ name: "new_wf", version: "" });
  });

  it("blurring with the same name keeps the pinned version", () => {
    const { latest, handleChangeAction } = start({ name: "wf", version: "4" });

    fireEvent.blur(screen.getByLabelText("Workflow name"), {
      target: { value: "wf" },
    });

    expect(handleChangeAction).toHaveBeenCalled();
    expect(body(latest)).toMatchObject({ name: "wf", version: "4" });
  });

  it.each([
    ["no workflows fetched, no name", new Map(), "", ["Latest"]],
    ["workflows fetched, no name", new Map([["a", [1, 2]]]), "", ["Latest"]],
    ["known name", new Map([["a", [1, 2]]]), "a", ["Latest", "1", "2"]],
    ["unknown name", new Map([["a", [1, 2]]]), "zzz", ["Latest"]],
  ])("version options: %s", (_label, workflows, name, expected) => {
    fetched.workflows = workflows as Map<string, number[]>;
    start({ name });

    expect(options("Workflow version")).toEqual(expected);
    fetched.workflows = new Map();
  });

  it("offers every fetched workflow name", () => {
    fetched.workflows = new Map([
      ["a", [1]],
      ["b", [1, 2]],
    ]);
    start();

    expect(options("Workflow name")).toEqual(["a", "b"]);
    fetched.workflows = new Map();
  });

  describe("idempotency", () => {
    it.each([
      ["no strategy yet", undefined, "RETURN_EXISTING"],
      ["strategy already saved", "FAIL", "FAIL"],
    ])("a key with %s", (_label, saved, expected) => {
      const { latest } = start({ idempotencyStrategy: saved });

      type("Idempotency key", "${event.payload.id}");

      expect(body(latest)).toMatchObject({
        idempotencyKey: "${event.payload.id}",
        idempotencyStrategy: expected,
      });
    });

    it.each(["FAIL", "RETURN_EXISTING", "FAIL_ON_RUNNING"])(
      "a key, then strategy %s, stores both",
      (strategy) => {
        const { latest } = start();

        type("Idempotency key", "k");
        fireEvent.change(screen.getByLabelText("Idempotency strategy"), {
          target: { value: strategy },
        });

        expect(body(latest)).toMatchObject({
          idempotencyKey: "k",
          idempotencyStrategy: strategy,
        });
      },
    );

    it.each([undefined, "FAIL", "RETURN_EXISTING", "FAIL_ON_RUNNING"])(
      "clearing the key drops strategy %s",
      (saved) => {
        const { latest } = start({
          idempotencyKey: "k",
          idempotencyStrategy: saved,
        });

        type("Idempotency key", "");

        expect(body(latest).idempotencyKey).toBe("");
        expect(body(latest).idempotencyStrategy).toBeUndefined();
      },
    );

    it("a strategy without a key is not stored", () => {
      const { latest } = start();

      fireEvent.change(screen.getByLabelText("Idempotency strategy"), {
        target: { value: "FAIL" },
      });

      expect(body(latest).idempotencyStrategy).toBeUndefined();
    });
  });

  it.each([
    ["Workflow correlation id", "correlationId", "${event.payload.orderId}"],
    ["Input variables", "input", { orderId: "${event.payload.orderId}" }],
    ["Tasks to domain mapping", "taskToDomain", { "*": "mydomain" }],
  ] as const)("writes %s without touching the rest", (label, field, value) => {
    const { latest } = start({ name: "wf", version: "2", idempotencyKey: "k" });

    if (typeof value === "string") type(label, value);
    else setMap(label, value);

    expect(body(latest)).toEqual({
      ...START_WORKFLOW_ACTION.start_workflow,
      name: "wf",
      version: "2",
      idempotencyKey: "k",
      [field]: value,
    });
  });
});

// ── Start agent ────────────────────────────────────────────────────────────────

describe("start_agent", () => {
  const start = (overrides: Record<string, unknown> = {}) =>
    renderAction(StartAgentActionForm, {
      ...clone(START_AGENT_ACTION),
      start_agent: { ...START_AGENT_ACTION.start_agent, ...overrides },
    });
  const body = (latest: () => any) => latest().start_agent;
  const MEDIA = "Media (one URL per line)";

  it.each([
    ["Agent name", "name"],
    ["Agent version", "version"],
    ["Prompt", "prompt"],
    ["Session ID", "sessionId"],
    ["Idempotency key", "idempotencyKey"],
  ])("writes %s on its own", (label, field) => {
    const { latest } = start();

    type(label, "${event.payload.x}");

    expect(body(latest)).toEqual({
      ...START_AGENT_ACTION.start_agent,
      [field]: "${event.payload.x}",
    });
  });

  it.each([
    ["Agent name", "name"],
    ["Agent version", "version"],
  ])("commits %s on blur", (label, field) => {
    const { latest } = start();

    fireEvent.blur(screen.getByLabelText(label), { target: { value: "v" } });

    expect(body(latest)[field]).toBe("v");
  });

  // Unlike start_workflow, a new agent name does not reset the version.
  it("a new agent name keeps a pinned version", () => {
    const { latest } = start({ name: "a", version: "2" });

    type("Agent name", "b");

    expect(body(latest)).toMatchObject({ name: "b", version: "2" });
  });

  it.each([
    ["one URL", "https://a/x.png", ["https://a/x.png"]],
    ["several URLs", "u1\nu2\nu3", ["u1", "u2", "u3"]],
    ["blank lines and padding", "  u1  \n\n\t\nu2\n", ["u1", "u2"]],
    ["only whitespace", "  \n \n", []],
    ["cleared", "", []],
  ])("splits media: %s", (_label, typed, expected) => {
    const { latest } = start({ media: ["previous"] });

    type(MEDIA, typed);

    expect(body(latest).media).toEqual(expected);
  });

  it("shows saved media one per line", () => {
    start({ media: ["u1", "u2"] });

    expect(screen.getByLabelText(MEDIA)).toHaveValue("u1\nu2");
  });

  it("writes context without touching the rest", () => {
    const { latest } = start({ name: "agent", prompt: "p", media: ["u"] });

    setMap("Context", { ticket: "${event.payload.ticket}" });

    expect(body(latest)).toEqual({
      ...START_AGENT_ACTION.start_agent,
      name: "agent",
      prompt: "p",
      media: ["u"],
      context: { ticket: "${event.payload.ticket}" },
    });
  });

  it.each([
    ["no agents fetched, no name", [], "", []],
    ["agents fetched, no name", [{ name: "a", version: 1 }], "", []],
    [
      "known name, versions sorted",
      [
        { name: "a", version: 2 },
        { name: "a", version: 1 },
        { name: "b", version: 5 },
      ],
      "a",
      ["1", "2"],
    ],
    ["unknown name", [{ name: "a", version: 1 }], "zzz", []],
  ])("version options: %s", (_label, agents, name, expected) => {
    fetched.agents = agents;
    start({ name });

    expect(
      JSON.parse(
        screen.getByLabelText("Agent version").getAttribute("data-options")!,
      ),
    ).toEqual(expected);
    fetched.agents = [];
  });

  it("offers fetched agent names once each, sorted", () => {
    fetched.agents = [
      { name: "b", version: 1 },
      { name: "a", version: 1 },
      { name: "b", version: 2 },
    ];
    start();

    expect(
      JSON.parse(
        screen.getByLabelText("Agent name").getAttribute("data-options")!,
      ),
    ).toEqual(["a", "b"]);
    fetched.agents = [];
  });
});

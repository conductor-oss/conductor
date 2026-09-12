/**
 * The Task tab must expose every field the Code tab's JSON carries, otherwise
 * a setting can only be reached by hand-editing raw JSON. These tests cover
 * the four that had no control: maxRetryDelaySeconds, backoffJitterMs,
 * totalTimeoutSeconds and taskStatusListenerEnabled.
 *
 * They assert against the form machine's context, which is what the Code tab
 * renders — so a control wired to a misspelled field name fails here. The
 * compiler cannot catch that: modifiedTaskDefinition reaches the form through
 * a loosely typed xstate ActorRef and is effectively `any`.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { interpret } from "xstate";
import { Provider as ThemeProvider } from "theme/material/provider";
import TaskDefinitionForm from "pages/definition/task/form/TaskDefinitionForm";
import { taskDefinitionFormMachine } from "pages/definition/task/form/state/machine";
import { TaskRetryLogic, TaskTimeoutPolicy } from "pages/definition/task/state";
import { TaskDefinitionDto } from "types/TaskDefinition";

vi.mock("utils/query", async (importOriginal) => ({
  ...(await importOriginal<typeof import("utils/query")>()),
  useFetch: () => ({ data: [], refetch: vi.fn() }),
}));

const baseTaskDefinition = {
  name: "my_task",
  description: "",
  retryCount: 3,
  retryDelaySeconds: 60,
  retryLogic: TaskRetryLogic.FIXED,
  backoffScaleFactor: 1,
  timeoutSeconds: 3600,
  timeoutPolicy: TaskTimeoutPolicy.TIME_OUT_WF,
  responseTimeoutSeconds: 600,
  pollTimeoutSeconds: 3600,
  rateLimitPerFrequency: 0,
  rateLimitFrequencyInSeconds: 1,
  concurrentExecLimit: 0,
  inputKeys: [],
  outputKeys: [],
  inputTemplate: {},
} as unknown as TaskDefinitionDto;

const renderForm = (overrides: Partial<TaskDefinitionDto> = {}) => {
  const taskDefinition = {
    ...baseTaskDefinition,
    ...overrides,
  } as TaskDefinitionDto;
  const service = interpret(
    taskDefinitionFormMachine.withContext({
      modifiedTaskDefinition: taskDefinition,
      originTaskDefinition: taskDefinition,
    }),
  ).start();

  render(
    <ThemeProvider>
      <TaskDefinitionForm formActor={service as never} />
    </ThemeProvider>,
  );

  return {
    /** What the Code tab would show. */
    definition: () =>
      service.getSnapshot().context.modifiedTaskDefinition as Record<
        string,
        unknown
      >,
    json: () =>
      (
        service.getSnapshot().context as unknown as {
          modifiedTaskDefinitionString?: string;
        }
      ).modifiedTaskDefinitionString ?? "",
  };
};

const field = (label: string) =>
  screen.getByLabelText(label) as HTMLInputElement;

const setNumber = (label: string, value: string) =>
  fireEvent.change(field(label), { target: { value } });

describe("TaskDefinitionForm — fields that were JSON-only", () => {
  it("shows the stored values for all four fields", () => {
    renderForm({
      maxRetryDelaySeconds: 120,
      backoffJitterMs: 250,
      totalTimeoutSeconds: 7200,
      taskStatusListenerEnabled: false,
    });

    expect(field("Max retry delay seconds")).toHaveValue("120");
    expect(field("Backoff jitter ms")).toHaveValue("250");
    expect(field("Total Timeout Seconds")).toHaveValue("7200");
    expect(
      screen.getByLabelText("Enable task status listener"),
    ).not.toBeChecked();
  });

  it("writes maxRetryDelaySeconds back into the definition", () => {
    const { definition, json } = renderForm({ maxRetryDelaySeconds: 0 });

    setNumber("Max retry delay seconds", "45");

    expect(definition().maxRetryDelaySeconds).toBe(45);
    expect(json()).toContain('"maxRetryDelaySeconds": 45');
  });

  it("writes backoffJitterMs back into the definition", () => {
    const { definition } = renderForm({ backoffJitterMs: 0 });

    setNumber("Backoff jitter ms", "500");

    expect(definition().backoffJitterMs).toBe(500);
  });

  it("writes totalTimeoutSeconds back into the definition", () => {
    const { definition } = renderForm({ totalTimeoutSeconds: 0 });

    setNumber("Total Timeout Seconds", "900");

    expect(definition().totalTimeoutSeconds).toBe(900);
  });

  it("toggles taskStatusListenerEnabled", () => {
    const { definition } = renderForm({ taskStatusListenerEnabled: true });

    fireEvent.click(screen.getByLabelText("Enable task status listener"));

    expect(definition().taskStatusListenerEnabled).toBe(false);
  });

  it("reads an absent taskStatusListenerEnabled as on, matching the server default", () => {
    renderForm();

    expect(screen.getByLabelText("Enable task status listener")).toBeChecked();
  });

  it("keeps the retry delay cap and jitter editable under a FIXED retry policy", () => {
    // Both are applied by the server for every retry policy, unlike the
    // backoff scale factor, which only applies to the backoff policies.
    renderForm({ retryLogic: TaskRetryLogic.FIXED });

    expect(field("Max retry delay seconds")).toBeEnabled();
    expect(field("Backoff jitter ms")).toBeEnabled();
    expect(field("Backoff scale factor")).toBeDisabled();
  });
});

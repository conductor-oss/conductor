/**
 * Save used to be disabled whenever the description was empty, with nothing on
 * screen explaining why. helpers.test.ts pins the rule that replaced it, but
 * only by omission — isSaveDisabled takes no description at all. This asserts
 * it where a description actually exists: the rendered Save button.
 *
 * The real task definition and form machines run, with the fetch service
 * stubbed; only leaf UI is replaced.
 */
import "@testing-library/jest-dom";
import { render, waitFor } from "@testing-library/react";
import { interpret } from "xstate";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { taskDefinitionMachine } from "pages/definition/task/state/machine";
import { TaskDefinitionFormEventType } from "pages/definition/task/form/state/types";
import { TASK_FORM_MACHINE_ID } from "pages/definition/task/state/helpers";
import { TaskDefinitionDto } from "types/TaskDefinition";

vi.mock("components/features/auth", () => ({
  useAuth: () => ({ isTrialExpired: false }),
}));

vi.mock(
  "../EditorPanel/TaskFormTab/forms/TestTaskButton/OpenTestTaskButton",
  () => ({ OpenTestTaskButton: () => null }),
);

vi.mock("components/ui/buttons/ConductorSplitButton", () => ({
  default: ({
    children,
    disabled,
    id,
  }: {
    children?: React.ReactNode;
    disabled?: boolean;
    id?: string;
  }) => (
    <button type="button" id={id} disabled={disabled}>
      {children}
    </button>
  ),
}));

const TASK_WITHOUT_DESCRIPTION = {
  name: "my_task",
  description: "",
  retryCount: 3,
  retryDelaySeconds: 60,
  retryLogic: "FIXED",
  timeoutSeconds: 3600,
  timeoutPolicy: "TIME_OUT_WF",
  responseTimeoutSeconds: 600,
  ownerEmail: "owner@orkes.io",
} as unknown as TaskDefinitionDto;

/**
 * Boots the page's machine on an existing definition, so Save renders through
 * the form-state wrapper rather than the new-definition split button.
 */
const renderButtons = async (taskDefinition: TaskDefinitionDto) => {
  const { default: TaskDefinitionButtons } =
    await import("./TaskDefinitionButtons");
  const service = interpret(
    taskDefinitionMachine
      .withConfig({
        services: {
          fetchTaskDefinitionByNameService: async () => taskDefinition,
        },
      })
      .withContext({
        ...taskDefinitionMachine.context,
        isNewTaskDef: false,
        modifiedTaskDefinition: taskDefinition,
        originTaskDefinition: taskDefinition,
      }),
  ).start();

  render(
    <ThemeProvider>
      <TaskDefinitionButtons taskDefActor={service as never} />
    </ThemeProvider>,
  );

  await waitFor(() =>
    expect(service.children.get(TASK_FORM_MACHINE_ID)).toBeTruthy(),
  );
  return { service };
};

/** Edits a field other than the description, so there is something to save. */
const editSomethingElse = (service: {
  children: Map<string, { send: (event: unknown) => void }>;
}) => {
  service.children.get(TASK_FORM_MACHINE_ID)!.send({
    type: TaskDefinitionFormEventType.HANDLE_CHANGE_TASK_FORM,
    name: "timeoutSeconds",
    value: 1200,
  });
};

const saveButton = () => document.querySelector("#task-save-btn");

describe("TaskDefinitionButtons — description is not required to save", () => {
  beforeEach(() => vi.clearAllMocks());

  it("enables Save on an edited definition whose description is empty", async () => {
    const { service } = await renderButtons(TASK_WITHOUT_DESCRIPTION);

    editSomethingElse(service as never);

    await waitFor(() => expect(saveButton()).not.toBeDisabled());
  });

  it("enables Save just the same when a description is present", async () => {
    const { service } = await renderButtons({
      ...TASK_WITHOUT_DESCRIPTION,
      description: "does something useful",
    });

    editSomethingElse(service as never);

    await waitFor(() => expect(saveButton()).not.toBeDisabled());
  });

  it("still disables Save when nothing has been edited", async () => {
    // The remaining reason Save is blocked, so the test above is not passing
    // for want of any gate at all.
    await renderButtons(TASK_WITHOUT_DESCRIPTION);

    await waitFor(() => expect(saveButton()).toBeDisabled());
  });

  it("clearing the description does not disable Save", async () => {
    const { service } = await renderButtons({
      ...TASK_WITHOUT_DESCRIPTION,
      description: "about to be removed",
    });

    service.children.get(TASK_FORM_MACHINE_ID)!.send({
      type: TaskDefinitionFormEventType.HANDLE_CHANGE_TASK_FORM,
      name: "description",
      value: "",
    });

    await waitFor(() => expect(saveButton()).not.toBeDisabled());
  });
});

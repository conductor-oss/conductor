import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ActorRef } from "xstate";
import { Provider as ThemeProvider } from "theme/material/provider";
import AddTaskSidebar from "./AddTaskSidebar";
import { RichAddMenuTabs, RichAddTaskMenuEvents } from "./state/types";

const { hook, perform } = vi.hoisted(() => ({
  hook: vi.fn(),
  perform: vi.fn(),
}));
vi.mock("./state/hook", () => ({ useRichAddTaskMenu: hook }));
vi.mock("./supportedTasks", () => ({ getALL_TASKS: () => [] }));
vi.mock("./iconsForTaskTypes", () => ({ iconForTaskTypeMap: {} }));
vi.mock("components/IntegrationIcon", () => ({ IntegrationIcon: () => null }));
vi.mock("plugins/registry", () => ({
  pluginRegistry: { getNewIntegrationModal: () => null },
}));
vi.mock("pages/definition/state", async () => ({
  WorkflowEditContext: (await import("react")).createContext({}),
}));
vi.mock("components/providers/messageContext", async () => ({
  MessageContext: (await import("react")).createContext({
    setMessage: vi.fn(),
  }),
}));
vi.mock("pages/definition/state/taskModifier/taskModifier", () => ({
  buildDataForOperation: () => ({ position: "selected-edge" }),
}));
vi.mock("pages/definition/state/usePerformOperationOnDefintion", () => ({
  usePerformOperationOnDefinition: () => ({ handlePerformOperation: perform }),
}));
const root = { name: "AWS Lambda", integrationType: "aws-lambda" };
const saved = {
  name: "Production Lambda",
  integrationType: "aws-lambda",
  description: "Saved connection",
};
beforeEach(() => {
  vi.clearAllMocks();
  hook.mockReturnValue([
    {
      supportedIntegrations: [],
      availableIntegrations: [saved],
      integrationDefs: [root],
      integrationDrillDownMenu: { isOpen: true, selectedRootIntegration: root },
      nodes: [],
      workerMenuItems: [],
      subWorkflowMenuItems: [],
      selectedTab: RichAddMenuTabs.INTEGRATIONS_TAB,
      isFetching: false,
      searchQuery: "",
    },
    {
      refetchIntegrations: vi.fn(),
      handleUpdateIntegrationDrillDown: vi.fn(),
      handleTyping: vi.fn(),
    },
  ]);
});
it("inserts a real CONNECTED_APP task with the chosen connection but no guessed operation", () => {
  render(
    <ThemeProvider>
      <AddTaskSidebar
        open
        richAddTaskMenuActor={
          { send: vi.fn() } as unknown as ActorRef<RichAddTaskMenuEvents>
        }
      />
    </ThemeProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: /Production Lambda/ }));
  expect(perform).toHaveBeenCalledWith(
    expect.objectContaining({
      position: "selected-edge",
      operation: {
        payload: expect.objectContaining({
          type: "CONNECTED_APP",
          name: "aws-lambda",
          inputParameters: {
            integrationName: "Production Lambda",
            integrationType: "aws-lambda",
          },
        }),
      },
    }),
  );
});

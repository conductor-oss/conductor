import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { IntegrationDrillDownContent } from "./IntegrationDrillDownContent";
import { ActorRef } from "xstate";
import { RichAddTaskMenuEvents } from "./state/types";

const { hook, update, fetchTools } = vi.hoisted(() => ({
  hook: vi.fn(),
  update: vi.fn(),
  fetchTools: vi.fn(),
}));
vi.mock("./state/hook", () => ({ useRichAddTaskMenu: hook }));
vi.mock("components/IntegrationIcon", () => ({ IntegrationIcon: () => null }));
const root = { name: "AWS Lambda", integrationType: "aws-lambda" };
const saved = {
  name: "Production Lambda",
  integrationType: "aws-lambda",
  description: "Saved connection",
};
const show = (add = vi.fn(), create = vi.fn()) => {
  render(
    <IntegrationDrillDownContent
      richAddTaskMenuActor={{} as ActorRef<RichAddTaskMenuEvents>}
      onAddIntegrationTask={add}
      onAddNewIntegration={create}
    />,
  );
  return { add, create };
};
beforeEach(() => {
  vi.clearAllMocks();
  hook.mockReturnValue([
    {
      availableIntegrations: [
        saved,
        { name: "Other", integrationType: "slack" },
      ],
      integrationDefs: [root],
      integrationDrillDownMenu: { isOpen: true, selectedRootIntegration: root },
      searchQuery: "",
    },
    {
      handleUpdateIntegrationDrillDown: update,
      handleFetchIntegrationTools: fetchTools,
    },
  ]);
});
it("adds the selected saved connection directly without fetching or selecting an operation", () => {
  const { add } = show();
  fireEvent.click(screen.getByRole("button", { name: /Production Lambda/ }));
  expect(add).toHaveBeenCalledWith(saved);
  expect(fetchTools).not.toHaveBeenCalled();
  expect(screen.queryByText("Other")).not.toBeInTheDocument();
  expect(screen.queryByText(/Tools/)).not.toBeInTheDocument();
});
it.each(["Enter", " "])(
  "supports keyboard connection selection with %s",
  (key) => {
    const { add } = show();
    fireEvent.keyDown(
      screen.getByRole("button", { name: /Production Lambda/ }),
      { key },
    );
    expect(add).toHaveBeenCalledWith(saved);
  },
);
it("keeps creating a connection available", () => {
  const { create } = show();
  fireEvent.click(screen.getByRole("button", { name: "Add New" }));
  expect(create).toHaveBeenCalledWith(root);
});
it("shows an empty saved-connection state after filtering", () => {
  const [state, actions] = hook();
  hook.mockReturnValue([{ ...state, searchQuery: "missing" }, actions]);
  show();
  expect(screen.getByText("No saved connections found")).toBeInTheDocument();
});

import { fireEvent, render, screen } from "@testing-library/react";
import { vi } from "vitest";
import { createMachine, interpret } from "xstate";
import { pluginRegistry } from "plugins/registry";
import { useFetch } from "utils/query";
import QuickAddMenu from "./QuickAddMenu";
import { richAddTaskMenuMachine } from "./state/machine";

vi.mock("utils/query", async (original) => ({
  ...(await original<typeof import("utils/query")>()),
  useFetch: vi.fn(),
}));

it("searches the live catalog by provider name and type, then opens that provider's connection list", () => {
  vi.spyOn(pluginRegistry, "getNewIntegrationModal").mockReturnValue(
    () => null,
  );
  const provider = {
    name: "Cloud Storage",
    type: "object-store",
    enabled: true,
    category: "MCP",
  };
  vi.mocked(useFetch).mockReturnValue({
    data: [
      provider,
      { ...provider, name: "Inactive provider", enabled: false },
      { ...provider, name: "Broker", category: "MESSAGE_BROKER" },
    ],
  } as ReturnType<typeof useFetch>);
  const menu = richAddTaskMenuMachine.withConfig({
    services: {
      fetchForMCPIntegrations: async () => ({
        supportedIntegrations: [provider],
        availableIntegrations: [],
      }),
    },
  });
  const parent = interpret(
    createMachine({ invoke: { id: "menu", src: menu } }),
  ).start();
  const actor = parent.state.children.menu;
  const anchor = document.body.appendChild(document.createElement("button"));
  try {
    render(<QuickAddMenu anchorEl={anchor} richAddTaskMenuActor={actor} />);
    expect(screen.getByText("Connected Apps")).toBeInTheDocument();
    expect(screen.getAllByText(provider.name)).toHaveLength(1);
    expect(screen.queryByText("Inactive provider")).toBeNull();
    expect(screen.queryByText("Broker")).toBeNull();
    const search = screen.getByPlaceholderText("Search tasks...");
    fireEvent.change(search, { target: { value: "List Objects" } });
    expect(screen.queryByText(provider.name)).toBeNull();
    for (const value of ["cloud storage", "object-store"]) {
      fireEvent.change(search, { target: { value } });
      expect(screen.getAllByText(provider.name)).toHaveLength(1);
    }
    fireEvent.click(screen.getByText(provider.name));
    expect(actor.getSnapshot().context).toMatchObject({
      menuType: "advanced",
      integrationDrillDownMenu: {
        isOpen: true,
        level: "integrations",
        selectedRootIntegration: {
          name: provider.name,
          integrationType: provider.type,
        },
      },
    });
  } finally {
    anchor.remove();
    parent.stop();
    vi.restoreAllMocks();
  }
});

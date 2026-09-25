import { fireEvent, render, screen } from "@testing-library/react";
import AgentDefinition from "./AgentDefinition";

const useFetch = vi.fn();

vi.mock("react-router", () => ({
  useParams: () => ({ name: "research%20agent", version: "2" }),
}));
vi.mock("utils/query", () => ({
  useFetch: (...args: unknown[]) => useFetch(...args),
}));
vi.mock("pages/execution/AgentExecution/AgentDefinitionView", () => ({
  AgentDefinitionDiagram: ({ agentDef }: any) => (
    <div>Diagram for {agentDef.name}</div>
  ),
}));
vi.mock("components/ReactJson", () => ({
  default: ({ src }: any) => <div>JSON for {src.name}</div>,
}));
vi.mock("components/layout/SectionHeader", () => ({
  default: ({ title }: { title: string }) => <h1>{title}</h1>,
}));

describe("AgentDefinition", () => {
  it("loads the selected version and renders the agent diagram", () => {
    useFetch.mockReturnValue({
      data: { name: "research agent", model: "openai/gpt-5" },
      isFetching: false,
      isError: false,
    });

    render(<AgentDefinition />);

    expect(useFetch).toHaveBeenCalledWith("/agent/research%20agent?version=2", {
      when: true,
    });
    expect(screen.getByText("Diagram for research agent")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "JSON" }));
    expect(screen.getByText("JSON for research agent")).toBeInTheDocument();
  });
});

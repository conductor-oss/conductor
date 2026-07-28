import { fireEvent, render, screen } from "@testing-library/react";
import AgentDefinitions from "./AgentDefinitions";

const navigate = vi.fn();

vi.mock("react-router", () => ({
  useNavigate: () => navigate,
}));

vi.mock("utils/query", () => ({
  useFetch: () => ({
    data: [
      {
        name: "research agent",
        version: 2,
        description: "Researches a topic",
      },
    ],
    isFetching: false,
    refetch: vi.fn(),
  }),
}));

vi.mock("components", () => ({
  DataTable: ({ columns, data }: any) => {
    const nameColumn = columns.find((column: any) => column.id === "name");
    return <>{nameColumn.renderer(data[0].name, data[0])}</>;
  },
  NavLink: ({ children, path }: any) => <a href={path}>{children}</a>,
  Paper: ({ children }: any) => <div>{children}</div>,
}));

vi.mock("components/layout/SectionHeader", () => ({
  default: ({ actions }: any) => <div>{actions}</div>,
}));
vi.mock("components/ui/layout/SectionHeaderActions", () => ({
  default: ({ buttons }: any) => (
    <>
      {buttons.map((button: any) => (
        <button key={button.label} onClick={button.onClick}>
          {button.label}
        </button>
      ))}
    </>
  ),
}));
vi.mock("components/icons/AddIcon", () => ({ default: () => null }));
vi.mock("components/ui/Header", () => ({ default: () => null }));
vi.mock("components/ui/NoDataComponent", () => ({ default: () => null }));
vi.mock("components/ui/layout/SectionContainer", () => ({
  default: ({ children }: any) => <div>{children}</div>,
}));
describe("AgentDefinitions", () => {
  it("links an agent name to its definition", () => {
    render(<AgentDefinitions />);

    expect(
      screen.getByRole("link", { name: "research agent" }),
    ).toHaveAttribute("href", "/agents/research%20agent/2");
  });

  it("opens the Markdown-backed create-agent guide", () => {
    render(<AgentDefinitions />);

    fireEvent.click(screen.getByRole("button", { name: "Create Agent" }));

    expect(navigate).toHaveBeenCalledWith(
      "/agents/new?language=python&framework=native",
    );
  });
});

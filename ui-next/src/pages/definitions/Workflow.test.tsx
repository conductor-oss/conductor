import { render, screen } from "@testing-library/react";
import WorkflowDefinitions from "./Workflow";

const navigate = vi.fn();

vi.mock("react-router", () => ({
  useNavigate: () => navigate,
}));

vi.mock("react-helmet", () => ({
  Helmet: ({ children }: any) => <>{children}</>,
}));

vi.mock("utils/query", () => ({
  useWorkflowDefListItems: () => ({
    data: [
      {
        name: "order_fulfillment",
        version: 3,
        description: "Fulfills an order",
        createTime: 1700000000000,
      },
    ],
    isFetching: false,
    refetch: vi.fn(),
  }),
  useActionWithPath: () => ({ mutate: vi.fn() }),
}));

vi.mock("utils/hooks/useCustomPagination", () => ({
  default: () => [
    { filterParam: "", pageParam: "", searchParam: "" },
    {
      setFilterParam: vi.fn(),
      setSearchParam: vi.fn(),
      handlePageChange: vi.fn(),
    },
  ],
}));

vi.mock("utils/hooks/usePushHistory", () => ({
  usePushHistory: () => vi.fn(),
}));

vi.mock("utils/flags", () => ({
  featureFlags: { isEnabled: () => false },
  FEATURES: {
    PLAYGROUND: "PLAYGROUND",
    TAG_VISIBILITY: "TAG_VISIBILITY",
    HIDE_IMPORT_BPMN: "HIDE_IMPORT_BPMN",
  },
}));

vi.mock("components/features/auth", () => ({
  useAuth: () => ({ isTrialExpired: false }),
}));

vi.mock("components/providers/messageContext", () => ({
  MessageContext: { Provider: ({ children }: any) => <>{children}</> },
}));

vi.mock("react", async () => {
  const actual = await vi.importActual<typeof import("react")>("react");
  return { ...actual, useContext: () => ({ setMessage: vi.fn() }) };
});

vi.mock("components", () => ({
  Button: ({ children, onClick }: any) => (
    <button onClick={onClick}>{children}</button>
  ),
  DataTable: ({ columns, data }: any) => {
    const nameColumn = columns.find(
      (column: any) => column.id === "workflow_name",
    );
    return <>{nameColumn.renderer(data[0].name, data[0])}</>;
  },
  IconButton: ({ children, onClick }: any) => (
    <button onClick={onClick}>{children}</button>
  ),
  NavLink: ({ children, path }: any) => <a href={path}>{children}</a>,
  Paper: ({ children }: any) => <div>{children}</div>,
}));

vi.mock("components/ui/Header", () => ({ default: () => null }));
vi.mock("components/ui/NoDataComponent", () => ({ default: () => null }));
vi.mock("components/ui/SnackbarMessage", () => ({
  SnackbarMessage: () => null,
}));
vi.mock("components/ui/dialogs/ConfirmChoiceDialog", () => ({
  default: () => null,
}));
vi.mock("components/features/tags/AddTagDialog", () => ({
  default: () => null,
}));
vi.mock("components/ui/TagList", () => ({ default: () => null }));
vi.mock("components/icons/PlayIcon", () => ({ default: () => null }));
vi.mock("components/ui/layout/SectionContainer", () => ({
  default: ({ children }: any) => <div>{children}</div>,
}));
vi.mock("components/layout/SectionHeader", () => ({
  default: ({ actions }: any) => <div>{actions}</div>,
}));
vi.mock("components/ui/layout/SectionHeaderActions", () => ({
  default: () => null,
}));
vi.mock(
  "pages/executions/SplitWorkflowDefinitionButton/SplitWorkflowDefinitionButton",
  () => ({ default: () => null }),
);
vi.mock("pages/executions/SplitWorkflowDefinitionButton/ImportBpmnButton", () => ({
  default: () => null,
}));
vi.mock("pages/runWorkflow/runWorkflowUtils", () => ({
  removeDeletedWorkflow: vi.fn(),
}));
vi.mock("./dialog/CloneWorkflowDialog", () => ({ default: () => null }));

describe("WorkflowDefinitions", () => {
  it("links a workflow name to its definition from the list endpoint", () => {
    render(<WorkflowDefinitions />);

    expect(
      screen.getByRole("link", { name: "order_fulfillment" }),
    ).toHaveAttribute("href", "/workflowDef/order_fulfillment");
  });
});

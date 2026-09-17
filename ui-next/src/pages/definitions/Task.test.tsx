import { render, screen } from "@testing-library/react";
import TaskDefinitions from "./Task";

// Every task the user can read. The "Executable?" column is derived by probing
// a second, narrower endpoint, so these rows must render regardless of it.
const readableTasks = [
  { name: "send_email", description: "Sends an email" },
  { name: "charge_card", description: "Charges a card" },
];

let executeQuery: { data?: unknown } = { data: [] };

vi.mock("utils/query", () => ({
  useFetch: (path: string) =>
    path.includes("access=EXECUTE")
      ? { ...executeQuery, isFetching: false, refetch: vi.fn() }
      : { data: readableTasks, isFetching: false, refetch: vi.fn() },
  useAction: () => ({ mutate: vi.fn(), isLoading: false }),
  useActionWithPath: () => ({ mutate: vi.fn() }),
}));

vi.mock("components", async () => {
  const { forwardRef } = await import("react");
  // Tooltip needs children that can hold a ref.
  const button = forwardRef<HTMLButtonElement, any>(({ children }, ref) => (
    <button ref={ref}>{children}</button>
  ));
  return {
    Button: button,
    IconButton: button,
    NavLink: ({ children }: any) => <a>{children}</a>,
    Paper: ({ children }: any) => <div>{children}</div>,
    DataTable: ({ columns, data }: any) => {
      const column = columns.find((c: any) => c.id === "executable");
      return (
        <ul>
          {data.map((row: any) => (
            <li key={row.name} data-testid={`executable-${row.name}`}>
              {column.renderer(row[column.name], row)}
            </li>
          ))}
        </ul>
      );
    },
  };
});

vi.mock("utils/hooks/useCustomPagination", () => ({
  default: () => [
    { pageParam: "", searchParam: "" },
    { setSearchParam: vi.fn(), handlePageChange: vi.fn() },
  ],
}));
vi.mock("utils/hooks/usePushHistory", () => ({
  usePushHistory: () => vi.fn(),
}));
vi.mock("components/features/auth", () => ({
  useAuth: () => ({ isTrialExpired: false }),
}));
vi.mock("react-helmet", () => ({ Helmet: () => null }));
vi.mock("components/ui/Header", () => ({ default: () => null }));
vi.mock("components/ui/NoDataComponent", () => ({ default: () => null }));
vi.mock("components/ui/SnackbarMessage", () => ({
  SnackbarMessage: () => null,
}));
vi.mock("components/ui/TagList", () => ({ default: () => null }));
vi.mock("components/icons/AddIcon", () => ({ default: () => null }));
vi.mock("components/features/tags/AddTagDialog", () => ({
  default: () => null,
}));
vi.mock("components/ui/dialogs/ConfirmChoiceDialog", () => ({
  default: () => null,
}));
vi.mock("./dialog/CloneDialog", () => ({ default: () => null }));
vi.mock("components/layout/SectionHeader", () => ({ default: () => null }));
vi.mock("components/ui/layout/SectionHeaderActions", () => ({
  default: () => null,
}));
vi.mock("components/ui/layout/SectionContainer", () => ({
  default: ({ children }: any) => <div>{children}</div>,
}));

const executableLabel = (name: string) =>
  screen.getByTestId(`executable-${name}`).textContent;

describe("TaskDefinitions executable column", () => {
  it("marks a task executable only when the execute query returns it", () => {
    executeQuery = { data: [{ name: "send_email" }] };

    render(<TaskDefinitions />);

    expect(executableLabel("send_email")).toBe("Yes");
    expect(executableLabel("charge_card")).toBe("No");
  });

  it("marks every readable task as not executable when none are executable", () => {
    executeQuery = { data: [] };

    render(<TaskDefinitions />);

    expect(executableLabel("send_email")).toBe("No");
    expect(executableLabel("charge_card")).toBe("No");
  });

  it("keeps readable tasks listed and claims nothing when the execute query has no result", () => {
    executeQuery = { data: undefined };

    render(<TaskDefinitions />);

    expect(executableLabel("send_email")).toBe("Unknown");
    expect(executableLabel("charge_card")).toBe("Unknown");
  });
});

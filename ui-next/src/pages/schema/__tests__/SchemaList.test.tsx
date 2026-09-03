import { fireEvent, render, screen } from "@testing-library/react";

import { SchemaList } from "../list/SchemaList";

const mutate = vi.fn();
const refetch = vi.fn();

const SCHEMAS = [
  {
    name: "order",
    version: 1,
    type: "JSON",
    data: {},
    createTime: 1000,
  },
  {
    name: "order",
    version: 3,
    type: "JSON",
    data: { type: "object" },
    createTime: 3000,
  },
  {
    name: "shipment",
    version: 1,
    type: "AVRO",
    data: {},
    createTime: 2000,
  },
];

vi.mock("utils/hooks/useGetSchemas", () => ({
  useGetSchemas: () => ({ data: SCHEMAS, isFetching: false, refetch }),
}));

vi.mock("utils/query", () => ({
  useActionWithPath: () => ({ mutate, isLoading: false }),
}));

vi.mock("utils/hooks/useCustomPagination", () => ({
  default: () => [
    { filterParam: "", pageParam: "", searchParam: "" },
    { setSearchParam: vi.fn(), handlePageChange: vi.fn() },
  ],
}));

vi.mock("utils/hooks/usePushHistory", () => ({
  usePushHistory: () => vi.fn(),
}));

vi.mock("utils/hooks/useToastMessage", () => ({
  useToastMessage: () => ({ toastMessage: vi.fn() }),
}));

vi.mock("components", () => ({
  DataTable: ({ columns, data }: any) => (
    <table>
      <tbody>
        {data.map((row: any) => (
          <tr key={row.name}>
            {columns.map((column: any) => (
              <td key={column.id}>
                {column.renderer
                  ? column.renderer(row[column.name], row)
                  : String(row[column.name])}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  ),
  ProgressHeading: () => null,
}));

vi.mock("components/ui/NavLink", () => ({
  default: ({ children, path }: any) => <a href={path}>{children}</a>,
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
vi.mock("components/ui/layout/SectionContainer", () => ({
  default: ({ children }: any) => <div>{children}</div>,
}));
vi.mock("components/icons/AddIcon", () => ({ default: () => null }));
vi.mock("components/ui/NoDataComponent", () => ({ default: () => null }));
vi.mock("components/ui/dialogs/ConfirmChoiceDialog", () => ({
  default: ({ message, handleConfirmationValue }: any) => (
    <div>
      <div>{message}</div>
      <button onClick={() => handleConfirmationValue(true)}>Confirm</button>
    </div>
  ),
}));
vi.mock("pages/definitions/dialog/CloneDialog", () => ({
  default: ({ name, onSuccess }: any) => (
    <button onClick={() => onSuccess({ name: `${name}` })}>
      Clone as {name}
    </button>
  ),
}));

describe("SchemaList", () => {
  beforeEach(() => {
    mutate.mockClear();
  });

  it("shows one row per schema, carrying its latest version and history depth", () => {
    render(<SchemaList />);

    const orderLink = screen.getByRole("link", { name: "order" });
    expect(orderLink).toHaveAttribute("href", "/schemas/order");

    const orderRow = orderLink.closest("tr")!;
    expect(orderRow).toHaveTextContent("3");
    // Two versions of "order" exist; the row says so.
    expect(orderRow).toHaveTextContent("2");
    expect(screen.getAllByRole("link")).toHaveLength(2);
  });

  it("lists a schema of a type this server cannot validate", () => {
    render(<SchemaList />);

    const shipmentRow = screen
      .getByRole("link", { name: "shipment" })
      .closest("tr")!;
    expect(shipmentRow).toHaveTextContent("AVRO");
  });

  it("deletes every version of a schema from its row", () => {
    const { container } = render(<SchemaList />);

    fireEvent.click(container.querySelector("#delete-order-btn")!);
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    expect(mutate).toHaveBeenCalledWith({
      method: "delete",
      path: "/schema/order",
    });
  });

  it("warns that a delete from the row takes every version with it", () => {
    const { container } = render(<SchemaList />);

    fireEvent.click(container.querySelector("#delete-order-btn")!);

    expect(screen.getByText(/All 2 versions will be removed/)).toBeVisible();
  });

  it("clones a schema as a fresh version 1, without the source's history", () => {
    const { container } = render(<SchemaList />);

    fireEvent.click(container.querySelector("#clone-order-btn")!);
    fireEvent.click(screen.getByRole("button", { name: /^Clone as/ }));

    expect(mutate).toHaveBeenCalledWith({
      method: "post",
      path: "/schema",
      body: JSON.stringify([
        { name: "order_1", version: 1, type: "JSON", data: { type: "object" } },
      ]),
    });
  });
});

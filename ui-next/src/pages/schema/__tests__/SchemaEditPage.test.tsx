import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import { useController } from "react-hook-form";
import { MemoryRouter, Route, Routes } from "react-router";

import { SchemaEditPage } from "../edit/SchemaEditPage";

const mutate = vi.fn();
const refetch = vi.fn();
const toastMessage = vi.fn();

const JSON_SCHEMA = {
  name: "order",
  version: 2,
  type: "JSON",
  data: { type: "object" },
};

const AVRO_SCHEMA = {
  name: "shipment",
  version: 1,
  type: "AVRO",
  data: { type: "record" },
};

const EDITED_SCHEMA = {
  name: "order",
  version: 2,
  type: "JSON",
  data: { type: "object", properties: { total: { type: "integer" } } },
};

const FETCHED: Record<string, unknown> = {
  "/schema/order/2": JSON_SCHEMA,
  "/schema/shipment/1": AVRO_SCHEMA,
};

vi.mock("utils/query", () => ({
  useFetch: (path: string, options: any) => {
    const enabled = options?.enabled !== false;
    const data = enabled ? FETCHED[path] : undefined;
    useEffect(() => {
      if (data) {
        options?.onSuccess?.(data);
      }
    }, [data]);
    return { data, isFetching: false, refetch, isError: false };
  },
  useActionWithPath: () => ({ mutate, isLoading: false }),
}));

vi.mock("utils/hooks/useEntityAvailableVersions", () => ({
  useEntityAvailableVersions: () => ({
    availableVersions: [1, 2],
    refetchAvailableVersions: vi.fn(),
    isFetchingAvailableVersions: false,
  }),
}));

vi.mock("utils/hooks/useToastMessage", () => ({
  useToastMessage: () => ({ toastMessage }),
}));
vi.mock("utils/hooks/usePushHistory", () => ({
  usePushHistory: () => vi.fn(),
}));
vi.mock("utils/hooks/useReplaceHistory", () => ({
  useReplaceHistory: () => vi.fn(),
}));

vi.mock("components", () => ({ ProgressHeading: () => null }));
vi.mock("components/BlockNavigationWithConfirmation", () => ({
  default: () => null,
}));
vi.mock("components/icons/DownloadIcon", () => ({ default: () => null }));
vi.mock("components/icons/ResetIcon", () => ({ default: () => null }));
vi.mock("components/icons/SaveIcon", () => ({ default: () => null }));
vi.mock("components/ui/layout/SectionContainer", () => ({
  default: ({ header, children }: any) => (
    <div>
      {header}
      {children}
    </div>
  ),
}));
vi.mock("components/layout/section/ConductorSectionHeader", () => ({
  ConductorSectionHeader: ({ title, buttonsComponent }: any) => (
    <div>
      <h1>{title}</h1>
      {buttonsComponent}
    </div>
  ),
}));
vi.mock("components/ui/buttons/ConductorSplitButton", () => ({
  default: ({ children, disabled, options, primaryOnClick }: any) => (
    <>
      <button disabled={disabled} onClick={primaryOnClick}>
        {children}
      </button>
      {options.map((option: any) => (
        <button key={option.label} disabled={disabled} onClick={option.onClick}>
          {option.label}
        </button>
      ))}
    </>
  ),
}));
vi.mock("components/ui/dialogs/ConfirmChoiceDialog", () => ({
  default: ({ message, handleConfirmationValue }: any) => (
    <div>
      <div>{message}</div>
      <button onClick={() => handleConfirmationValue(true)}>Confirm</button>
    </div>
  ),
}));
// Monaco does not run in jsdom, so stand in a textarea bound to the same form
// field. Typing into it dirties the form exactly as editing the real editor does.
vi.mock("components/ui/react-hook-form/ReactHookFormEditor", () => ({
  default: function MockEditor({ control, name, options }: any) {
    const { field } = useController({ control, name });
    return (
      <textarea
        data-testid="editor"
        data-readonly={String(!!options?.readOnly)}
        readOnly={!!options?.readOnly}
        value={field.value ?? ""}
        onChange={(event) => field.onChange(event.target.value)}
      />
    );
  },
}));

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/schemas/:schemaName/:version?"
          element={<SchemaEditPage />}
        />
      </Routes>
    </MemoryRouter>,
  );

describe("SchemaEditPage", () => {
  beforeEach(() => {
    mutate.mockClear();
    toastMessage.mockClear();
  });

  /** Type an edit into the editor and wait for the form to register it. */
  const editSchema = async () => {
    fireEvent.change(screen.getByTestId("editor"), {
      target: { value: JSON.stringify(EDITED_SCHEMA, null, 2) },
    });
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Save" })).toBeEnabled(),
    );
  };

  it("asks before overwriting the version being viewed, then saves it in place", async () => {
    renderAt("/schemas/order/2");
    await editSchema();

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(
      screen.getByText(/overwriting a version that workflows or tasks/i),
    ).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    // handleSubmit resolves asynchronously, so the request lands a tick later.
    await waitFor(() =>
      expect(mutate).toHaveBeenCalledWith({
        method: "post",
        path: "/schema",
        body: JSON.stringify([EDITED_SCHEMA]),
      }),
    );
  });

  it("lets the server allocate the number when saving as a new version", async () => {
    renderAt("/schemas/order/2");
    await editSchema();

    fireEvent.click(
      screen.getByRole("button", { name: "Save as new version" }),
    );

    // No confirmation, and no client-computed version number: the server
    // allocates it, so two people saving at once cannot pick the same one.
    await waitFor(() =>
      expect(mutate).toHaveBeenCalledWith({
        method: "post",
        path: "/schema?newVersion=true",
        body: JSON.stringify([EDITED_SCHEMA]),
      }),
    );
  });

  it("deletes only the version being viewed", () => {
    renderAt("/schemas/order/2");

    fireEvent.click(screen.getByRole("button", { name: "Delete version" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    expect(mutate).toHaveBeenCalledWith({
      method: "delete",
      path: "/schema/order/2",
    });
  });

  it("shows a schema this server cannot validate read-only, and says why", () => {
    renderAt("/schemas/shipment/1");

    expect(
      screen.getByText(
        /AVRO schemas are stored but not validated by this server/,
      ),
    ).toBeVisible();
    expect(screen.getByTestId("editor")).toHaveAttribute(
      "data-readonly",
      "true",
    );
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("edits a JSON schema without a read-only notice", () => {
    renderAt("/schemas/order/2");

    expect(screen.queryByText(/not validated by this server/)).toBeNull();
    expect(screen.getByTestId("editor")).toHaveAttribute(
      "data-readonly",
      "false",
    );
  });
});

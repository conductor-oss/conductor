import { render, screen } from "@testing-library/react";

import { Provider as ThemeProvider } from "theme/material/provider";
import { useFetch } from "utils/query";
import { ConductorNameVersionField } from "./ConductorNameVersionField";

vi.mock("utils/query", () => ({
  useFetch: vi.fn(),
}));

type Options = { name: string; version: number }[];

/** A listing the server answered with. */
const served = (data: Options) => {
  (useFetch as ReturnType<typeof vi.fn>).mockReturnValue({
    data,
    isSuccess: true,
    isError: false,
    refetch: vi.fn(),
  });
};

/** The listing request failed: 404, 403, 5xx. */
const failed = () => {
  (useFetch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: undefined,
    isSuccess: false,
    isError: true,
    refetch: vi.fn(),
  });
};

/** Still in flight, or idle because the query is disabled. */
const pending = () => {
  (useFetch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: undefined,
    isSuccess: false,
    isError: false,
    refetch: vi.fn(),
  });
};

const renderField = () =>
  render(
    <ThemeProvider>
      <ConductorNameVersionField
        label="Input Schema"
        optionsUrl="/schema"
        value={{ name: "order", version: 1 }}
        showErrorIfItemNotInList
        mapOptions={(data: { name: string; version: number }[] | undefined) =>
          (data ?? []).map(({ name, version }) => ({
            name,
            versions: [version],
          }))
        }
      />
    </ThemeProvider>,
  );

const nameInput = () => screen.getByLabelText(/Input Schema/);

describe("ConductorNameVersionField", () => {
  it("flags a name the served listing does not hold", () => {
    served([{ name: "shipment", version: 1 }]);

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "true");
  });

  it("flags a name against a served listing that is empty", () => {
    // A server that holds no schemas still contradicts the reference, and the
    // commercial UI asserts this: its "non-existing schema" test expects the
    // field to render red on a server with an empty registry.
    served([]);

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "true");
  });

  it("does not flag a name the served listing holds", () => {
    served([{ name: "order", version: 1 }]);

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "false");
  });

  it("flags when the listing request failed", () => {
    // The reference cannot be verified, which is worth showing rather than
    // rendering a field that looks checked and was not.
    failed();

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "true");
  });

  it("does not flag while the request is in flight or idle", () => {
    // react-query leaves a disabled query idle rather than loading, so neither
    // state may flag: both would mark every definition carrying a reference as
    // broken on the way to an answer.
    pending();

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "false");
  });
});

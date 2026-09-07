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

/**
 * How the Task Definition form uses this field: no reference selected yet, and no opt-in
 * to reference checking.
 */
const renderEmptyUncheckedField = () =>
  render(
    <ThemeProvider>
      <ConductorNameVersionField
        label="Input Schema"
        optionsUrl="/schema"
        mapOptions={(data: { name: string; version: number }[] | undefined) =>
          (data ?? []).map(({ name, version }) => ({
            name,
            versions: [version],
          }))
        }
      />
    </ThemeProvider>,
  );

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
    // An empty listing is still an answer. A reference to a name the server
    // does not list is contradicted whether the listing has other names in it
    // or none at all, so an empty registry is not a reason to withhold the flag.
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

  /**
   * A picker with nothing selected has no reference to be wrong about. Flagging it would
   * paint every schema picker red on a server that does not serve /schema at all, with no
   * value on screen to explain the colour.
   */
  it("does not flag an empty picker when the listing request failed", () => {
    failed();

    renderEmptyUncheckedField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "false");
  });

  it("says why, rather than only turning red", () => {
    served([{ name: "somethingElse", version: 1 }]);

    renderField();

    expect(screen.getByText(/"order" is not in the list/i)).toBeVisible();
  });
});

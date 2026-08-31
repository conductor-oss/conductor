import { render, screen } from "@testing-library/react";

import { Provider as ThemeProvider } from "theme/material/provider";
import { useFetch } from "utils/query";
import { ConductorNameVersionField } from "./ConductorNameVersionField";

vi.mock("utils/query", () => ({
  useFetch: vi.fn(),
}));

const mockOptions = (
  result: Partial<{ data: unknown; isSuccess: boolean }>,
): void => {
  (useFetch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: result.data,
    isSuccess: result.isSuccess ?? false,
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
  it("flags a name the loaded option list does not contain", () => {
    mockOptions({ data: [{ name: "shipment", version: 1 }], isSuccess: true });

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "true");
  });

  it("does not flag a name while the option list has not loaded", () => {
    // What an unreachable registry endpoint looks like: no options, no success.
    // Flagging here would mark every definition that carries a schema as broken.
    mockOptions({ data: undefined, isSuccess: false });

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "false");
  });

  it("does not flag a name the loaded option list contains", () => {
    mockOptions({ data: [{ name: "order", version: 1 }], isSuccess: true });

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "false");
  });
});

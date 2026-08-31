import { render, screen } from "@testing-library/react";

import { Provider as ThemeProvider } from "theme/material/provider";
import { useFetch } from "utils/query";
import { ConductorNameVersionField } from "./ConductorNameVersionField";

vi.mock("utils/query", () => ({
  useFetch: vi.fn(),
}));

const mockOptions = (data: { name: string; version: number }[] | undefined) => {
  (useFetch as ReturnType<typeof vi.fn>).mockReturnValue({
    data,
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
  it("flags a name the option list contradicts", () => {
    mockOptions([{ name: "shipment", version: 1 }]);

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "true");
  });

  it("does not flag a name the option list contains", () => {
    mockOptions([{ name: "order", version: 1 }]);

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "false");
  });

  it("does not flag against an empty option list", () => {
    // What a server holding no schemas looks like, and what an unreachable
    // endpoint and an in-flight request look like too. Flagging here would mark
    // every definition that carries a reference as broken.
    mockOptions([]);

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "false");
  });

  it("does not flag before the option list has loaded", () => {
    mockOptions(undefined);

    renderField();

    expect(nameInput()).toHaveAttribute("aria-invalid", "false");
  });
});

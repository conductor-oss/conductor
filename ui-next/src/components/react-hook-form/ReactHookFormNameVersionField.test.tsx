import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { Provider as ThemeProvider } from "theme/material/provider";
import { useFetch } from "utils/query";
import ReactHookFormNameVersionField from "./ReactHookFormNameVersionField";

// Mocking the data fetching hook
vi.mock("utils/query", () => ({
  useFetch: vi.fn(),
}));

describe("ReactHookFormNameVersionField", () => {
  const setup = (props = {}) => {
    const TestComponent = () => {
      (useFetch as ReturnType<typeof vi.fn>).mockReturnValue({
        data: [
          { name: "option0", versions: [1, 2] },
          { name: "option1", versions: [1, 2, 3] },
          { name: "option2", versions: [1, 2] },
        ],
      });
      const { control, watch } = useForm({
        defaultValues: { test: { name: "", version: "" } },
      });
      const watchedValues = watch();

      return (
        <ThemeProvider>
          <ReactHookFormNameVersionField
            {...props}
            nameField={{
              id: "name-field",
            }}
            versionField={{
              id: "version-field",
            }}
            optionsUrl="test"
            label="Name"
            name="test"
            control={control}
          />
          <div data-testid="form-value">{JSON.stringify(watchedValues)}</div>
        </ThemeProvider>
      );
    };

    return render(<TestComponent />);
  };

  const getFormValue = () =>
    JSON.parse(screen.getByTestId("form-value").textContent || "");

  test("renders the autocomplete and select inputs", () => {
    setup();
    expect(document.getElementById("name-field")).toBeInTheDocument();
    expect(document.getElementById("version-field")).toBeInTheDocument();
  });

  test("applies input transform function", () => {
    const inputTransform = vi
      .fn()
      .mockReturnValue({ name: "option1", version: 1 });
    setup({ inputTransform });

    expect(document.getElementById("name-field")).toHaveValue("option1");
    expect(document.getElementById("version-field")).toHaveTextContent(
      "Version 1",
    );
    expect(inputTransform).toHaveBeenCalled();
    expect(getFormValue()).toEqual({ test: { name: "", version: "" } });
  });

  test("calls output transform and onChange callback on change", () => {
    const outputTransform = vi
      .fn()
      .mockReturnValue({ name: "option0", version: 1 });
    const onChangeCallback = vi.fn();
    setup({ outputTransform, onChangeCallback });

    const nameField = document.getElementById("name-field");
    const versionField = document.getElementById("version-field");

    if (!nameField || !versionField) {
      throw new Error("Name field or Version field not found");
    }

    // Open the autocomplete options
    fireEvent.focus(nameField);
    fireEvent.keyDown(nameField, { key: "ArrowDown" });

    // Select the second option
    const option = screen.getByText("option1");
    fireEvent.click(option);

    expect(outputTransform).toHaveBeenCalledWith(
      { name: "option1", version: undefined },
      expect.any(Object),
    );
    expect(onChangeCallback).toHaveBeenCalledWith({
      name: "option0",
      version: 1,
    });
    expect(getFormValue()).toEqual({
      test: { name: "option0", version: 1 },
    });
  });
});

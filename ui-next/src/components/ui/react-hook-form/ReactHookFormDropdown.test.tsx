import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { Provider as ThemeProvider } from "theme/material/provider";
import ReactHookFormDropdown from "./ReactHookFormDropdown";

describe("ReactHookFormDropdown", () => {
  const options = ["Option 1", "Option 2", "Option 3"];

  const setup = (props: any = {}) => {
    const TestComponent = () => {
      const { control, watch } = useForm({
        defaultValues: { test: undefined },
      });
      const watchedValues = watch();
      return (
        <ThemeProvider>
          <ReactHookFormDropdown
            {...props}
            name="test"
            control={control}
            options={options}
          />
          <div data-testid="form-value">{JSON.stringify(watchedValues)}</div>
        </ThemeProvider>
      );
    };

    return render(<TestComponent />);
  };

  const getFormValue = () =>
    JSON.parse(screen.getByTestId("form-value").textContent || "");

  test("renders the dropdown", () => {
    setup();
    const input = screen.getByRole("combobox");
    expect(input).toBeInTheDocument();
  });

  test("applies input transform function", () => {
    const inputTransform = vi.fn().mockReturnValue("Option 3");
    setup({ inputTransform });
    const input = screen.getByRole("combobox");
    expect(input).toHaveValue("Option 3");
    expect(inputTransform).toHaveBeenCalled();
    expect(getFormValue()).toEqual({ test: undefined });
  });

  test("calls output transform and onChange callback on change", () => {
    const outputTransform = vi.fn().mockReturnValue("Option 3");
    const onChangeCallback = vi.fn();
    setup({ outputTransform, onChangeCallback });

    const input = screen.getByRole("combobox");
    fireEvent.change(input, { target: { value: "Option 1" } });

    // Open the autocomplete options
    fireEvent.focus(input);
    fireEvent.keyDown(input, { key: "ArrowDown" });

    // Select the first option
    const option = screen.getByText("Option 1");
    fireEvent.click(option);

    expect(outputTransform).toHaveBeenCalledWith(
      "Option 1",
      expect.any(Object),
    );
    expect(onChangeCallback).toHaveBeenCalledWith("Option 3");
    expect(getFormValue()).toEqual({ test: "Option 3" });
  });

  test("updates value correctly without transform functions", () => {
    setup();
    const input = screen.getByRole("combobox");
    expect(input).toHaveValue("");

    // Open the autocomplete options
    fireEvent.focus(input);
    fireEvent.keyDown(input, { key: "ArrowDown" });

    // Select the second option
    const option = screen.getByText("Option 2");
    fireEvent.click(option);

    expect(input).toHaveValue("Option 2");
    expect(getFormValue()).toEqual({ test: "Option 2" });
  });

  test("validates input value correctly", () => {
    setup();
    const input = screen.getByRole("combobox");
    expect(input).toHaveValue("");
    expect(getFormValue()).toEqual({ test: undefined });
  });

  test("handles multiple and freeSolo correctly", () => {
    setup({ multiple: false, freeSolo: true });
    const input = screen.getByRole("combobox");
    fireEvent.change(input, { target: { value: "free solo value" } });
    expect(input).toHaveValue("free solo value");
    expect(getFormValue()).toEqual({ test: "free solo value" });
  });
});

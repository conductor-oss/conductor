import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { Provider as ThemeProvider } from "theme/material/provider";
import ReactHookFormInput from "./ReactHookFormInput";

describe("ReactHookFormInput", () => {
  const setup = (props: any = {}) => {
    const TestComponent = () => {
      const { control, watch } = useForm({ defaultValues: { test: "" } });
      const watchedValues = watch();

      return (
        <ThemeProvider>
          <ReactHookFormInput {...props} name="test" control={control} />
          <div data-testid="form-value">{JSON.stringify(watchedValues)}</div>
        </ThemeProvider>
      );
    };

    return render(<TestComponent />);
  };

  const getFormValue = () =>
    JSON.parse(screen.getByTestId("form-value").textContent || "");

  test("renders the input", () => {
    setup();
    const input = screen.getByRole("textbox");
    expect(input).toBeInTheDocument();
  });

  test("applies input transform function", () => {
    const inputTransform = vi.fn().mockReturnValue("transformed value");
    setup({ inputTransform });
    const input = screen.getByRole("textbox");
    expect(input).toHaveValue("transformed value");
    expect(inputTransform).toHaveBeenCalled();
    expect(getFormValue()).toEqual({ test: "" });
  });

  test("calls output transform and onChange callback on change", () => {
    const outputTransform = vi.fn().mockReturnValue("transformed output");
    const onChangeCallback = vi.fn();
    setup({ outputTransform, onChangeCallback });

    const input = screen.getByRole("textbox");
    fireEvent.change(input, { target: { value: "new value" } });

    expect(outputTransform).toHaveBeenCalledWith(
      "new value",
      expect.any(Object),
    );
    expect(onChangeCallback).toHaveBeenCalledWith("transformed output");
    expect(getFormValue()).toEqual({ test: "transformed output" });
  });

  test("updates value correctly without transform functions", () => {
    setup();
    const input = screen.getByRole("textbox");
    expect(input).toHaveValue("");
    fireEvent.change(input, { target: { value: "new value" } });
    expect(input).toHaveValue("new value");
    expect(getFormValue()).toEqual({ test: "new value" });
  });

  test("validates input value correctly", () => {
    setup();
    const input = screen.getByRole("textbox");
    expect(input).toHaveValue("");
    expect(getFormValue()).toEqual({ test: "" });
  });
});

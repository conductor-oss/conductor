import { render, screen } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { Provider as ThemeProvider } from "theme/material/provider";
import "@testing-library/jest-dom";
import ReactHookFormIdempotencyForm from "./ReactHookFormIdempotencyForm";

describe("ReactHookFormIdempotencyForm", () => {
  const setup = (props: any = {}) => {
    const TestComponent = () => {
      const { control, watch } = useForm({
        defaultValues: { idempotencyKey: "", idempotencyStrategy: undefined },
      });
      const watchedValues = watch();
      return (
        <ThemeProvider>
          <ReactHookFormIdempotencyForm
            {...props}
            name="idempotency"
            control={control}
          />
          <div data-testid="form-value">{JSON.stringify(watchedValues)}</div>
        </ThemeProvider>
      );
    };

    return render(<TestComponent />);
  };

  const getFormValue = () =>
    JSON.parse(screen.getByTestId("form-value").textContent || "{}");

  test("renders the form correctly", () => {
    setup();
    expect(screen.getByLabelText("Idempotency key")).toBeInTheDocument();
  });

  test("validates input value correctly", () => {
    setup();
    const input = screen.getByLabelText("Idempotency key");
    expect(input).toHaveValue("");
    expect(getFormValue()).toEqual({
      idempotencyKey: "",
      idempotencyStrategy: undefined,
    });
  });
});

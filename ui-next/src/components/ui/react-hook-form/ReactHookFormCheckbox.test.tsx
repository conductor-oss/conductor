import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { useForm } from "react-hook-form";
import ReactHookFormCheckbox from "./ReactHookFormCheckbox";

describe("ReactHookFormCheckbox", () => {
  const setup = (props: any = {}) => {
    const Wrapper = () => {
      const { control } = useForm({ defaultValues: { test: false } });
      return <ReactHookFormCheckbox {...props} name="test" control={control} />;
    };

    return render(<Wrapper />);
  };

  test("renders the checkbox", () => {
    setup();
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeInTheDocument();
  });

  test("applies input transform function", () => {
    const inputTransform = vi.fn().mockReturnValue(true);
    setup({ inputTransform });
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeChecked();
    expect(inputTransform).toHaveBeenCalled();
  });

  test("calls output transform and onChange callback on change", () => {
    const outputTransform = vi.fn().mockReturnValue(true);
    const onChangeCallback = vi.fn();
    setup({ outputTransform, onChangeCallback });

    const checkbox = screen.getByRole("checkbox");
    fireEvent.click(checkbox);
    expect(outputTransform).toHaveBeenCalledWith(true, expect.any(Object));
    expect(onChangeCallback).toHaveBeenCalledWith(true);
  });

  test("updates value correctly without transform functions", () => {
    setup();
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).not.toBeChecked();
    fireEvent.click(checkbox);
    expect(checkbox).toBeChecked();
  });

  test("validates input value correctly", () => {
    setup();
    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).not.toBeChecked();
  });
});

import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { PayloadFieldInput, WorkflowIdInput } from "./PayloadFieldInput";
import { PayloadFieldsContext } from "./payloadFields";

const renderWith = (fields: string[], ui: ReactNode) =>
  render(
    <ThemeProvider>
      <PayloadFieldsContext.Provider value={fields}>
        {ui}
      </PayloadFieldsContext.Provider>
    </ThemeProvider>,
  );

// The label is also rendered as text, so find the field by role and name.
const optionsOnFocus = (label: string) => {
  const input = screen.getByRole("combobox", { name: label });
  // Browser order for a click into the field: mousedown, then focus.
  fireEvent.mouseDown(input);
  fireEvent.focus(input);
  return screen.queryAllByRole("option").map((o) => o.textContent);
};

describe("PayloadFieldInput", () => {
  it("is a plain input with no sample fields", () => {
    renderWith([], <PayloadFieldInput label="Reason" value="" />);

    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Reason" })).toBeInTheDocument();
  });

  it("suggests only the sample's fields", () => {
    renderWith(
      ["${orderId}", "${status}"],
      <PayloadFieldInput label="Reason" value="" />,
    );

    expect(optionsOnFocus("Reason")).toEqual(["${orderId}", "${status}"]);
  });

  // Growing on focus and shrinking on blur moved the next control out from
  // under the click that blurred the field (e2e: Append checkbox missed).
  it("keeps a single-line field the same height when focused", () => {
    renderWith(["${orderId}"], <PayloadFieldInput label="Reason" value="" />);
    const input = screen.getByRole("combobox", { name: "Reason" });

    fireEvent.focus(input);

    expect(screen.getByRole("combobox", { name: "Reason" }).tagName).toBe(
      "INPUT",
    );
  });

  it("emits the picked reference", () => {
    const onTextInputChange = vi.fn();
    renderWith(
      ["${orderId}"],
      <PayloadFieldInput
        label="Reason"
        value=""
        onTextInputChange={onTextInputChange}
      />,
    );

    optionsOnFocus("Reason");
    fireEvent.click(screen.getByRole("option", { name: "${orderId}" }));

    expect(onTextInputChange).toHaveBeenLastCalledWith("${orderId}");
  });
});

describe("WorkflowIdInput", () => {
  it("suggests ${workflowInstanceId} even without a sample", () => {
    renderWith([], <WorkflowIdInput label="Workflow ID" value="" />);

    expect(optionsOnFocus("Workflow ID")).toEqual(["${workflowInstanceId}"]);
  });

  it("adds it after the sample's fields without duplicating it", () => {
    renderWith(
      ["${workflowInstanceId}", "${orderId}"],
      <WorkflowIdInput label="Workflow ID" value="" />,
    );

    expect(optionsOnFocus("Workflow ID")).toEqual([
      "${workflowInstanceId}",
      "${orderId}",
    ]);
  });
});

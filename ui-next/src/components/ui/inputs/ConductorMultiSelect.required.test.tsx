/**
 * CCOR-13473: the New Service form's "Allowed Methods" is validated as required
 * but showed no asterisk, because this component — unlike ConductorAutoComplete
 * — had no `required` prop to pass through to ConductorInput.
 */
import "@testing-library/jest-dom";
import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import ConductorMultiSelect from "./ConductorMultiSelect";

const renderSelect = (required?: boolean) =>
  render(
    <ThemeProvider>
      <ConductorMultiSelect
        label="Allowed Methods"
        options={["GET", "POST"]}
        onSelected={vi.fn()}
        allText="All Methods"
        value={[]}
        required={required}
      />
    </ThemeProvider>,
  );

// MUI renders the label twice — the floating <label> and the fieldset
// <legend> — so assert on the <label> specifically.
// MUI renders the label twice — the floating <label> and the fieldset
// <legend> — and separates the asterisk with a thin space, so assert on the
// <label> and don't pin the exact whitespace character.
const labelText = () =>
  (document.querySelector("label")?.textContent ?? "")
    .replace(/\s+/g, " ")
    .trim();

describe("ConductorMultiSelect required", () => {
  it("marks the label with an asterisk when required", () => {
    renderSelect(true);

    expect(labelText()).toBe("Allowed Methods *");
  });

  it("leaves the label unmarked by default", () => {
    renderSelect();

    expect(labelText()).toBe("Allowed Methods");
  });
});

/**
 * Every confirmation in the product renders through this dialog, destructive or not, so
 * the two treatments are pinned here.
 */
import { fireEvent, render, screen } from "@testing-library/react";

import ConfirmChoiceDialog from "./ConfirmChoiceDialog";

// ConductorInput autofocuses through a ref that jsdom leaves undefined.
vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ id, value, onTextInputChange }: any) => (
    <input
      id={id}
      value={value ?? ""}
      onChange={(event) => onTextInputChange?.(event.target.value)}
    />
  ),
}));

const renderDialog = (props = {}) => {
  const handleConfirmationValue = vi.fn();
  render(
    <ConfirmChoiceDialog
      header="Override ?"
      message="Something already exists."
      handleConfirmationValue={handleConfirmationValue}
      {...props}
    />,
  );
  return { handleConfirmationValue };
};

const badge = () => document.querySelector("#choice-dialog-icon");
const confirmBtn = () =>
  document.querySelector("#choice-dialog-confirm-btn") as HTMLButtonElement;
const cancelBtn = () =>
  document.querySelector("#choice-dialog-cancel-btn") as HTMLButtonElement;
const nameField = () =>
  document.querySelector(
    "#choice-dialog-confirmation-field",
  ) as HTMLInputElement;

describe("ConfirmChoiceDialog", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows the question it was given", () => {
    renderDialog();

    expect(screen.getByText("Override ?")).toBeInTheDocument();
    expect(screen.getByText("Something already exists.")).toBeInTheDocument();
  });

  it("confirms and cancels", () => {
    const { handleConfirmationValue } = renderDialog();

    fireEvent.click(confirmBtn());
    expect(handleConfirmationValue).toHaveBeenCalledWith(true);

    fireEvent.click(cancelBtn());
    expect(handleConfirmationValue).toHaveBeenCalledWith(false);
  });

  it("drops the cancel button when asked", () => {
    renderDialog({ hideCancelBtn: true });

    expect(cancelBtn()).toBeNull();
  });

  describe("an ordinary confirmation", () => {
    it("wears no destructive mark", () => {
      renderDialog();

      expect(badge()).toBeNull();
      expect(confirmBtn()).toHaveTextContent("Confirm");
    });

    it("is not gated behind typing anything", () => {
      renderDialog();

      expect(confirmBtn()).not.toBeDisabled();
    });
  });

  describe("a destructive confirmation", () => {
    it("is marked, and follows from the typed-name gate", () => {
      renderDialog({ isInputConfirmation: true, valueToBeDeleted: "thing" });

      expect(badge()).toBeInTheDocument();
      expect(confirmBtn()).toBeDisabled();

      fireEvent.change(nameField(), { target: { value: "thing" } });

      expect(confirmBtn()).not.toBeDisabled();
    });

    it("can be marked without a typed-name gate", () => {
      renderDialog({ destructive: true });

      expect(badge()).toBeInTheDocument();
      expect(confirmBtn()).not.toBeDisabled();
    });

    it("can be waived on a dialog that does gate on typing", () => {
      renderDialog({
        isInputConfirmation: true,
        valueToBeDeleted: "thing",
        destructive: false,
      });

      expect(badge()).toBeNull();
    });

    it("takes its own button label", () => {
      renderDialog({ destructive: true, confirmBtnLabel: "Delete version 3" });

      expect(confirmBtn()).toHaveTextContent("Delete version 3");
    });
  });
});

import { fireEvent, render, screen } from "@testing-library/react";
import { Provider as ThemeProvider } from "theme/material/provider";
import UIModal from "./UIModal";

const renderModal = (props: { disableBackdropClose?: boolean } = {}) => {
  const setOpen = vi.fn();
  render(
    <ThemeProvider>
      <UIModal
        open
        setOpen={setOpen}
        title="Test modal"
        enableCloseButton
        {...props}
      >
        <div>content</div>
      </UIModal>
    </ThemeProvider>,
  );
  return setOpen;
};

// MUI only treats a click as a backdrop click when mousedown and click both
// land on the dialog container itself (not on the paper inside it).
const clickOutside = () => {
  const container = document.querySelector(".MuiDialog-container")!;
  fireEvent.mouseDown(container);
  fireEvent.click(container);
};

const pressEscape = () => {
  fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
};

describe("UIModal", () => {
  it("closes on an outside click by default", () => {
    const setOpen = renderModal();
    clickOutside();
    expect(setOpen).toHaveBeenCalledWith(false);
  });

  it("stays open on an outside click with disableBackdropClose", () => {
    const setOpen = renderModal({ disableBackdropClose: true });
    clickOutside();
    expect(setOpen).not.toHaveBeenCalled();
  });

  it("still closes on Escape with disableBackdropClose", () => {
    const setOpen = renderModal({ disableBackdropClose: true });
    pressEscape();
    expect(setOpen).toHaveBeenCalledWith(false);
  });

  it("does not close when clicking inside the modal", () => {
    const setOpen = renderModal();
    fireEvent.mouseDown(screen.getByText("content"));
    fireEvent.click(screen.getByText("content"));
    expect(setOpen).not.toHaveBeenCalled();
  });
});

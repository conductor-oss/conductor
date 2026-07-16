import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider as ThemeProvider } from "theme/material/provider";
import SplitButton from "./ConductorSplitButton";

describe("ConductorSplitButton", () => {
  it("opens a compact menu and runs the selected secondary action", async () => {
    const onPrimaryClick = vi.fn();
    const onSecondaryClick = vi.fn();

    render(
      <ThemeProvider>
        <SplitButton
          options={[{ label: "Show as code", onClick: onSecondaryClick }]}
          primaryOnClick={onPrimaryClick}
        >
          Search
        </SplitButton>
      </ThemeProvider>,
    );

    const moreActions = screen.getByRole("button", { name: "More actions" });
    fireEvent.click(moreActions);

    const menu = screen.getByRole("menu");
    const popper = menu.closest(".MuiPopper-root");
    expect(popper).toHaveStyle({ width: "max-content" });
    expect(moreActions).toHaveAttribute("aria-expanded", "true");

    fireEvent.click(screen.getByRole("menuitem", { name: "Show as code" }));

    expect(onSecondaryClick).toHaveBeenCalledOnce();
    expect(onPrimaryClick).not.toHaveBeenCalled();
    expect(moreActions).not.toHaveAttribute("aria-expanded");
    await waitFor(() =>
      expect(screen.queryByRole("menu")).not.toBeInTheDocument(),
    );
  });
});

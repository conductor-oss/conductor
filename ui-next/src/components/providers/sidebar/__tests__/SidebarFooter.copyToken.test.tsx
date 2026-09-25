/**
 * CCOR-13474: "Copy Token" showed a success toast every time while the
 * clipboard was never written. The toast fired before the token was even read,
 * so a missing token or a rejected write still looked like a copy.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider as ThemeProvider } from "theme/material/provider";
import { getAccessToken } from "components/features/auth/tokenManagerJotai";
import { SidebarFooter } from "../SidebarFooter";

vi.mock("components/features/auth/tokenManagerJotai", () => ({
  getAccessToken: vi.fn(),
}));

vi.mock("../SidebarVersionBlock", () => ({
  SidebarVersionBlock: () => null,
}));

vi.mock("utils", () => ({
  FEATURES: { COPY_TOKEN: "COPY_TOKEN", PLAYGROUND: "PLAYGROUND" },
  featureFlags: { isEnabled: () => false },
}));

vi.mock("utils/logger", () => ({ logger: { error: vi.fn() } }));

vi.mock("components/ui/SnackbarMessage", () => ({
  SnackbarMessage: ({ id, message, severity }: any) => (
    <div data-testid={id} data-severity={severity}>
      {message}
    </div>
  ),
}));

const mockedGetAccessToken = getAccessToken as unknown as ReturnType<
  typeof vi.fn
>;

const renderFooter = () => {
  const setShowCopyAlert = vi.fn();
  render(
    <ThemeProvider>
      <SidebarFooter
        open
        isAuthenticated
        isMobile={false}
        user={{ given_name: "Dana" } as never}
        conductorUser={{ id: "dana@orkes.io" }}
        uiVersion="1.0.0"
        showCopyAlert={false}
        setShowCopyAlert={setShowCopyAlert}
      />
    </ThemeProvider>,
  );
  return { setShowCopyAlert };
};

const copyButton = () => document.querySelector("#user-info-copy-token-btn")!;

describe("SidebarFooter — Copy Token", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
  });

  it("writes the token and only then reports success", async () => {
    mockedGetAccessToken.mockReturnValue("a-real-token");
    const { setShowCopyAlert } = renderFooter();

    fireEvent.click(copyButton());

    await waitFor(() =>
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
        "a-real-token",
      ),
    );
    expect(setShowCopyAlert).toHaveBeenCalledWith(true);
  });

  it("reports an error instead of success when there is no token", async () => {
    mockedGetAccessToken.mockReturnValue(null);
    const { setShowCopyAlert } = renderFooter();

    fireEvent.click(copyButton());

    await waitFor(() =>
      expect(
        screen.getByTestId("copy-clipboard-error-popup"),
      ).toHaveTextContent("No access token to copy."),
    );
    expect(navigator.clipboard.writeText).not.toHaveBeenCalled();
    expect(setShowCopyAlert).not.toHaveBeenCalled();
  });

  it("reports an error when the clipboard write is rejected", async () => {
    mockedGetAccessToken.mockReturnValue("a-real-token");
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockRejectedValue(new Error("denied")) },
    });
    const { setShowCopyAlert } = renderFooter();

    fireEvent.click(copyButton());

    await waitFor(() =>
      expect(
        screen.getByTestId("copy-clipboard-error-popup"),
      ).toHaveTextContent("Could not copy to clipboard."),
    );
    expect(setShowCopyAlert).not.toHaveBeenCalled();
  });
});

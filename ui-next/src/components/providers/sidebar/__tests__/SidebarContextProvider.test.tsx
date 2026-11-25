/**
 * SidebarProvider must keep `children` mounted across auth transitions. It used
 * to swap between two different element types as the auth machine moved, which
 * remounted the whole app below it on every signed-in load.
 */
import "@testing-library/jest-dom";
import { act, render } from "@testing-library/react";
import { useContext, useEffect } from "react";
import { MemoryRouter } from "react-router";
import { Provider as ThemeProvider } from "theme/material/provider";
import { AuthContext } from "components/features/auth/context";
import { SidebarContext } from "../context/SidebarContext";
import { SidebarProvider } from "../context/SidebarContextProvider";

type AuthService = NonNullable<
  React.ContextType<typeof AuthContext>["authService"]
>;

type FakeSnapshot = {
  authenticated: boolean;
  noUserManagement: boolean;
  sidebarInit: boolean;
  children: Record<string, unknown>;
};

const sidebarActor = { id: "sidebarMachine" };

const flagState = vi.hoisted(() => ({ playground: false }));

vi.mock("utils/flags", async (importOriginal) => {
  const actual = await importOriginal<typeof import("utils/flags")>();
  return {
    ...actual,
    featureFlags: {
      ...actual.featureFlags,
      isEnabled: (feature: string) =>
        feature === actual.FEATURES.PLAYGROUND
          ? flagState.playground
          : actual.featureFlags.isEnabled(feature),
    },
  };
});

vi.mock("../../../../shared/state/selectors", () => ({
  isAuthenticated: (s: FakeSnapshot) => s.authenticated,
  noUserManagement: (s: FakeSnapshot) => s.noUserManagement,
  isSidebarInitialized: (s: FakeSnapshot) => s.sidebarInit,
}));

vi.mock("../../../../shared/PersistableSidebar/state/hook", () => ({
  useSidebarMenu: mockSidebarMenu,
}));

function mockSidebarMenu() {
  return {
    isSidebarExpanded: false,
    location: undefined,
    handleAnnouncementBanner: () => {},
    openedMenus: ["fromMachine"],
    setOpenedMenus: () => {},
    addMenu: () => {},
    removeMenu: () => {},
    toggleSidebar: () => {},
    isSidebarHidden: false,
    isSearchModalOpen: false,
    handleSearchModal: () => {},
    isBannerOpen: false,
  };
}

const createFakeAuthService = (initial: FakeSnapshot) => {
  let snapshot = initial;
  const listeners = new Set<(s: FakeSnapshot) => void>();
  return {
    get state() {
      return snapshot;
    },
    getSnapshot: () => snapshot,
    subscribe: (listener: (s: FakeSnapshot) => void) => {
      listeners.add(listener);
      return { unsubscribe: () => listeners.delete(listener) };
    },
    send: () => {},
    set(next: Partial<FakeSnapshot>) {
      snapshot = { ...snapshot, ...next };
      listeners.forEach((l) => l(snapshot));
    },
  };
};

const loading: FakeSnapshot = {
  authenticated: false,
  noUserManagement: false,
  sidebarInit: false,
  children: {},
};

describe("SidebarProvider", () => {
  const setup = (authService?: ReturnType<typeof createFakeAuthService>) => {
    const mounts = vi.fn();
    const seen: { isStateless: boolean; openedMenus: string[] }[] = [];
    const Child = () => {
      const ctx = useContext(SidebarContext);
      seen.push({ isStateless: ctx.isStateless, openedMenus: ctx.openedMenus });
      useEffect(() => {
        mounts();
      }, []);
      return null;
    };
    render(
      <ThemeProvider>
        <MemoryRouter>
          <AuthContext.Provider
            value={{ authService: authService as unknown as AuthService }}
          >
            <SidebarProvider>
              <Child />
            </SidebarProvider>
          </AuthContext.Provider>
        </MemoryRouter>
      </ThemeProvider>,
    );
    return { mounts, seen, last: () => seen[seen.length - 1] };
  };

  it("mounts children once while auth goes from loading to signed in", () => {
    const authService = createFakeAuthService(loading);
    const { mounts, last } = setup(authService);

    expect(mounts).toHaveBeenCalledTimes(1);
    expect(last().isStateless).toBe(true);

    act(() =>
      authService.set({
        sidebarInit: true,
        children: { sidebarMachine: sidebarActor },
      }),
    );
    act(() => authService.set({ authenticated: true }));

    expect(mounts).toHaveBeenCalledTimes(1);
    expect(last()).toEqual({
      isStateless: false,
      openedMenus: ["fromMachine"],
    });
  });

  it("serves the default value until the sidebar actor is ready, then the sidebar value", () => {
    const authService = createFakeAuthService({
      ...loading,
      authenticated: true,
    });
    const { last } = setup(authService);
    expect(last().isStateless).toBe(true);
    expect(last().openedMenus).toEqual(["helpMenu"]);

    act(() =>
      authService.set({
        sidebarInit: true,
        children: { sidebarMachine: sidebarActor },
      }),
    );
    expect(last().isStateless).toBe(false);
    expect(last().openedMenus).toEqual(["fromMachine"]);
  });

  it("settles on the sidebar value within one act", () => {
    const authService = createFakeAuthService(loading);
    const { seen } = setup(authService);
    seen.length = 0;
    act(() =>
      authService.set({
        authenticated: true,
        sidebarInit: true,
        children: { sidebarMachine: sidebarActor },
      }),
    );
    expect(seen[seen.length - 1].isStateless).toBe(false);
    expect(seen.length).toBeLessThanOrEqual(2);
  });

  it("reverts to the default value, without remounting, when the actor goes away", () => {
    const authService = createFakeAuthService({
      authenticated: true,
      noUserManagement: false,
      sidebarInit: true,
      children: { sidebarMachine: sidebarActor },
    });
    const { mounts, last } = setup(authService);
    expect(last().isStateless).toBe(false);

    act(() =>
      authService.set({
        authenticated: false,
        sidebarInit: false,
        children: {},
      }),
    );
    expect(last().isStateless).toBe(true);
    expect(mounts).toHaveBeenCalledTimes(1);
  });

  it("uses the sidebar value in no-user-management mode", () => {
    const authService = createFakeAuthService({
      authenticated: false,
      noUserManagement: true,
      sidebarInit: true,
      children: { sidebarMachine: sidebarActor },
    });
    const { last } = setup(authService);
    expect(last().isStateless).toBe(false);
  });

  it("serves the default value without an auth service", () => {
    const { mounts, last } = setup(undefined);
    expect(mounts).toHaveBeenCalledTimes(1);
    expect(last().isStateless).toBe(true);
  });

  it("uses the sidebar value in playground mode before auth is signed in", async () => {
    flagState.playground = true;
    vi.resetModules();
    const { SidebarProvider: PlaygroundProvider } =
      await import("../context/SidebarContextProvider");
    const { SidebarContext: Ctx } = await import("../context/SidebarContext");
    const { AuthContext: Auth } =
      await import("components/features/auth/context");
    const authService = createFakeAuthService({
      ...loading,
      children: { sidebarMachine: sidebarActor },
    });
    let value: { isStateless: boolean } | undefined;
    const Probe = () => {
      value = useContext(Ctx);
      return null;
    };
    try {
      render(
        <ThemeProvider>
          <MemoryRouter>
            <Auth.Provider
              value={{ authService: authService as unknown as AuthService }}
            >
              <PlaygroundProvider>
                <Probe />
              </PlaygroundProvider>
            </Auth.Provider>
          </MemoryRouter>
        </ThemeProvider>,
      );
      expect(value?.isStateless).toBe(false);
    } finally {
      flagState.playground = false;
    }
  });
});

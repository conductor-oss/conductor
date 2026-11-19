import { memo, useContext, useLayoutEffect, useMemo, useState } from "react";

import { Theme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useSelector } from "@xstate/react";
import {
  SidebarContext,
  SidebarContextState,
  SidebarProviderProps,
} from "components/providers/sidebar/context/SidebarContext";
import { useLocation } from "react-router";
import { featureFlags, FEATURES } from "utils/flags";
import { ActorRef, State } from "xstate";
import { AuthContext } from "components/features/auth/context";
import { useSidebarMenu } from "../../../../shared/PersistableSidebar/state/hook";
import { PersistableSidebarEvent } from "../../../../shared/PersistableSidebar/state/types";
import { AuthProviderMachineContext } from "../../../../shared/state";
import {
  isAuthenticated as getIsAuthenticated,
  noUserManagement as getIsNoUserManagement,
  isSidebarInitialized,
} from "../../../../shared/state/selectors";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);
const isAiStudioBannerFlagOn = featureFlags.isEnabled(
  FEATURES.SHOW_AI_STUDIO_BANNER_FLAG,
);
/**
 * Runs useSidebarMenu (which needs the sidebar actor) and publishes the
 * resulting context value up to SidebarProvider. It renders nothing, so it
 * can come and go without touching the position of `children`.
 */
const SidebarValueSource = memo(function SidebarValueSource({
  sidebarActor,
  isMobile,
  onValue,
}: {
  sidebarActor: ActorRef<PersistableSidebarEvent>;
  isMobile: boolean;
  onValue: (value: SidebarContextState | null) => void;
}) {
  const {
    isSidebarExpanded,
    location,
    handleAnnouncementBanner,
    openedMenus,
    setOpenedMenus,
    addMenu,
    removeMenu,
    toggleSidebar,
    isSidebarHidden,
    isSearchModalOpen,
    handleSearchModal,
    isBannerOpen,
  } = useSidebarMenu(sidebarActor, isMobile);

  const [isAiStudioBannerDismissed, setIsAiStudioBannerDismissed] = useState(
    () => {
      return localStorage.getItem("aiStudioBannerDismissed") !== null;
    },
  );

  const showAiStudioBanner = useMemo(() => {
    return isAiStudioBannerFlagOn && isPlayground && !isAiStudioBannerDismissed;
  }, [isAiStudioBannerDismissed]);

  const memoContextValue = useMemo(() => {
    const dismissAiStudioBanner = () => {
      localStorage.setItem("aiStudioBannerDismissed", Date.now().toString());
      setIsAiStudioBannerDismissed(true);
    };

    return {
      isStateless: false,
      open: isSidebarExpanded,
      openedMenus,
      setOpenedMenus,
      addMenu,
      removeMenu,
      isMobile,
      toggleMenu: toggleSidebar,
      hideSideBar: isSidebarHidden,
      isSearchModalOpen,
      setSearchModal: handleSearchModal,
      isBannerOpen,
      setBannerOpen: handleAnnouncementBanner,
      location,
      showAiStudioBanner: showAiStudioBanner,
      dismissAiStudioBanner,
      sidebarActor,
    };
  }, [
    isSidebarExpanded,
    openedMenus,
    setOpenedMenus,
    addMenu,
    removeMenu,
    isMobile,
    toggleSidebar,
    isSidebarHidden,
    isSearchModalOpen,
    handleSearchModal,
    isBannerOpen,
    handleAnnouncementBanner,
    location,
    showAiStudioBanner,
    sidebarActor,
  ]);

  // Layout effects commit before paint, so no frame is painted with the
  // default value once the actor exists (consumers render once with the
  // default before this publish).
  useLayoutEffect(() => {
    onValue(memoContextValue);
  }, [memoContextValue, onValue]);

  useLayoutEffect(() => () => onValue(null), [onValue]);

  return null;
});

// Only rendered when authService is available (selectors need it). Decides
// whether the sidebar machine should drive the context.
const SidebarAuthGate = ({
  authService,
  isMobile,
  onValue,
}: {
  authService: ActorRef<any>;
  isMobile: boolean;
  onValue: (value: SidebarContextState | null) => void;
}) => {
  const isAuthenticated = useSelector(authService, getIsAuthenticated);
  const noUserManagement = useSelector(authService, getIsNoUserManagement);
  const isSideBarState = useSelector(authService, isSidebarInitialized);
  const sidebarActor = useSelector(
    authService,
    (state: State<AuthProviderMachineContext>) =>
      state.children["sidebarMachine"],
  );

  const userManagementIsNotAvailable = noUserManagement && isSideBarState;
  const withUserManagement = isAuthenticated && isSideBarState;
  const withSidebarSupport =
    isPlayground && authService?.getSnapshot().children["sidebarMachine"];

  const active =
    userManagementIsNotAvailable || withUserManagement || withSidebarSupport;

  return active && sidebarActor ? (
    <SidebarValueSource
      sidebarActor={sidebarActor}
      isMobile={isMobile}
      onValue={onValue}
    />
  ) : null;
};

export const SidebarProvider = ({ children }: SidebarProviderProps) => {
  const { authService } = useContext(AuthContext);

  const isMobile = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down("sm"),
  );
  const location = useLocation();

  // Default context value for when authService is not available or not ready
  const defaultContextValue = useMemo(() => {
    return {
      isStateless: true,
      open: true,
      openedMenus: ["helpMenu"],
      setOpenedMenus: () => {},
      addMenu: () => {},
      removeMenu: () => {},
      isMobile,
      toggleMenu: () => {},
      hideSideBar: location.pathname === "/integrations/addIntegration",
      isSearchModalOpen: false,
      setSearchModal: () => {},
      isBannerOpen: true,
      setBannerOpen: () => {},
      dismissAiStudioBanner: () => {},
    };
  }, [isMobile, location.pathname]);

  // The sidebar machine's value, published by SidebarValueSource once the
  // sidebar actor exists. Null until then, and again if it goes away.
  const [sidebarValue, setSidebarValue] = useState<SidebarContextState | null>(
    null,
  );

  // The Provider and `children` stay at the same tree position whatever the
  // auth state does, so the app below is never remounted as auth settles.
  // Only the context value varies.
  return (
    <SidebarContext.Provider value={sidebarValue ?? defaultContextValue}>
      {authService ? (
        <SidebarAuthGate
          authService={authService}
          isMobile={isMobile}
          onValue={setSidebarValue}
        />
      ) : null}
      {children}
    </SidebarContext.Provider>
  );
};

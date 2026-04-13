import { ReactNode, useContext, useMemo, useState } from "react";

import { Theme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useSelector } from "@xstate/react";
import {
  SidebarContext,
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
const SidebarContextWrapper = ({
  sidebarActor,
  isMobile,
  children,
}: {
  sidebarActor: ActorRef<PersistableSidebarEvent>;
  isMobile: boolean;
  children: ReactNode;
}) => {
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

  return (
    <SidebarContext.Provider value={memoContextValue}>
      {children}
    </SidebarContext.Provider>
  );
};

// Inner component that uses useSelector (only rendered when authService is available)
const SidebarProviderWithAuth = ({
  children,
  authService,
  isMobile,
  defaultContextValue,
}: {
  children: ReactNode;
  authService: ActorRef<any>;
  isMobile: boolean;
  defaultContextValue: any;
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

  return userManagementIsNotAvailable ||
    withUserManagement ||
    withSidebarSupport ? (
    <SidebarContextWrapper sidebarActor={sidebarActor} isMobile={isMobile}>
      {children}
    </SidebarContextWrapper>
  ) : (
    <SidebarContext.Provider value={defaultContextValue}>
      {children}
    </SidebarContext.Provider>
  );
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

  // If authService is not available, use default context
  if (!authService) {
    return (
      <SidebarContext.Provider value={defaultContextValue}>
        {children}
      </SidebarContext.Provider>
    );
  }

  // authService is available, render the inner component with selectors
  return (
    <SidebarProviderWithAuth
      authService={authService}
      isMobile={isMobile}
      defaultContextValue={defaultContextValue}
    >
      {children}
    </SidebarProviderWithAuth>
  );
};

import { useSelector } from "@xstate/react";
import { useCallback, useEffect } from "react";
import { useLocation } from "react-router";
import { ActorRef, State } from "xstate";
import {
  PersistableSidebarEvent,
  PersistableSidebarEventTypes,
  PersistableSidebarStates,
  SidebarMachineContext,
} from "./types";

export const useSidebarMenu = (
  sidebarActor: ActorRef<PersistableSidebarEvent>,
  isMobile: boolean,
) => {
  const location = useLocation();

  const isSidebarExpanded = useSelector(
    sidebarActor,
    (state: State<SidebarMachineContext>) =>
      state.matches(PersistableSidebarStates.EXPANDED),
  );

  const openedMenus = useSelector(
    sidebarActor!,
    (state: State<SidebarMachineContext>) => state.context.openedMenus,
  );

  const isBannerOpen = useSelector(
    sidebarActor!,
    (state: State<SidebarMachineContext>) => state.context.isBannerOpen,
  );

  const isSearchModalOpen = useSelector(
    sidebarActor!,
    (state: State<SidebarMachineContext>) => state.context.isSearchModalOpen,
  );

  const handleAnnouncementBanner = (val: boolean) => {
    sidebarActor.send({
      type: PersistableSidebarEventTypes.TOGGLE_BANNER_EVENT,
      data: { val },
    });
  };

  const handleSearchModal = (val: boolean) => {
    sidebarActor.send({
      type: PersistableSidebarEventTypes.TOGGLE_SEARCH_MODAL_EVENT,
      data: { val },
    });
  };

  const expandSidebar = useCallback(() => {
    sidebarActor.send({
      type: PersistableSidebarEventTypes.EXPAND_SIDEBAR_EVENT,
    });
  }, [sidebarActor]);

  const collapseSidebar = useCallback(() => {
    sidebarActor?.send({
      type: PersistableSidebarEventTypes.COLLAPSE_SIDEBAR_EVENT,
    });
  }, [sidebarActor]);

  const addMenu = (id: string) => {
    sidebarActor.send({
      type: PersistableSidebarEventTypes.ADD_MENU_EVENT,
      data: { id },
    });
  };

  const removeMenu = (id: string) => {
    sidebarActor.send({
      type: PersistableSidebarEventTypes.REMOVE_MENU_EVENT,
      data: { id },
    });
  };

  const setOpenedMenus = (items: string[]) => {
    sidebarActor.send({
      type: PersistableSidebarEventTypes.ADD_MENUS_EVENT,
      data: { items },
    });
  };

  const toggleSidebar = () => {
    if (isSidebarExpanded) {
      collapseSidebar();
    } else {
      expandSidebar();
    }
  };

  useEffect(() => {
    if (isMobile) {
      collapseSidebar();
    }
  }, [collapseSidebar, isMobile, location.pathname]);

  return {
    openedMenus,
    isSidebarHidden:
      location.pathname === "/integrations/addIntegration" ||
      location.pathname === "/login",
    isBannerOpen,
    isSearchModalOpen,
    location,
    isSidebarExpanded,
    handleAnnouncementBanner,
    handleSearchModal,
    collapseSidebar,
    toggleSidebar,
    addMenu,
    removeMenu,
    setOpenedMenus,
  };
};

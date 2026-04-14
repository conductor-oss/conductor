import { ReactNode, createContext } from "react";
import { Location } from "react-router";
import { PersistableSidebarEvent } from "shared/PersistableSidebar/state/types";
import { ActorRef } from "xstate";

export interface SidebarProviderProps {
  children: ReactNode;
}

export interface SidebarContextState {
  open: boolean;
  isMobile: boolean;
  setOpen?: (val: boolean) => void;
  openedMenus: string[];
  setOpenedMenus: (openMenus: string[]) => void;
  addMenu: (id: string) => void;
  removeMenu: (id: string) => void;
  toggleMenu: () => void;
  hideSideBar: boolean;
  isSearchModalOpen: boolean;
  setSearchModal: (val: boolean) => void;
  isBannerOpen: boolean;
  setBannerOpen: (val: boolean) => void;
  location?: Location;
  isStateless: boolean;
  showAiStudioBanner?: boolean;
  dismissAiStudioBanner?: () => void;
  sidebarActor?: ActorRef<PersistableSidebarEvent>;
}

export const SidebarContext = createContext<SidebarContextState>({
  isStateless: false, // If its controlled by a machine, then this is true
  open: false,
  isMobile: false,
  setOpen: () => {},
  openedMenus: [],
  setOpenedMenus: () => {},
  addMenu: () => {},
  removeMenu: () => {},
  toggleMenu: () => {},
  hideSideBar: false,
  isSearchModalOpen: false,
  setSearchModal: () => {},
  isBannerOpen: false,
  setBannerOpen: () => {},
});

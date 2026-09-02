import { ActorRef } from "xstate";
import { PersistableSidebarEvent } from "./types";
export declare const useSidebarMenu: (sidebarActor: ActorRef<PersistableSidebarEvent>, isMobile: boolean) => {
    openedMenus: string[];
    isSidebarHidden: boolean;
    isBannerOpen: boolean;
    isSearchModalOpen: boolean;
    location: import("react-router").Location<any>;
    isSidebarExpanded: boolean;
    handleAnnouncementBanner: (val: boolean) => void;
    handleSearchModal: (val: boolean) => void;
    collapseSidebar: () => void;
    toggleSidebar: () => void;
    addMenu: (id: string) => void;
    removeMenu: (id: string) => void;
    setOpenedMenus: (items: string[]) => void;
};

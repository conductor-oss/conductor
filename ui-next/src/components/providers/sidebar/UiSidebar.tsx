/**
 * UiSidebar - Main sidebar component for Conductor UI
 *
 * This component defines the core (OSS) sidebar menu items and merges in
 * any additional items registered by plugins (enterprise features).
 *
 * Core OSS items:
 * - Executions submenu (Workflow, Scheduler, Queue Monitor)
 * - Run Workflow button
 * - Definitions submenu (Workflow, Task, Event Handler, Scheduler)
 * - API Docs
 * - Help menu
 *
 * Enterprise items are registered via plugins and merged at runtime.
 */

import { Sidebar } from "components/providers/sidebar";
import { useAnnouncementBanner } from "components/layout/header/bannerUtils";
import { MenuItemType } from "components/providers/sidebar/types";
import { pluginRegistry } from "plugins/registry";
import { FunctionComponent, useContext, useMemo } from "react";
import { FEATURES, featureFlags } from "utils";
import { SidebarContext } from "./context/SidebarContext";
import { useAuth } from "components/features/auth";
import { getCoreSidebarItems } from "./sidebarCoreItems";
import { mergePluginSidebarItems } from "./sidebarMenuUtils";

const customLogo = featureFlags.getValue(FEATURES.CUSTOM_LOGO_URL);

type UISidebarProps = {
  /** undefined = loading (skeleton), null = error/unavailable, string = loaded */
  apiVersion?: string | null;
  releaseVersion?: string;
};

export const UISidebar: FunctionComponent<UISidebarProps> = ({
  apiVersion,
  releaseVersion,
}) => {
  const {
    open,
    setSearchModal,
    toggleMenu,
    isMobile,
    isBannerOpen,
    showAiStudioBanner,
  } = useContext(SidebarContext);

  const { isTrialExpired, trialExpiryDate, isAnnouncementBannerDismissed } =
    useAuth();
  const { showBanner } = useAnnouncementBanner(
    isTrialExpired,
    trialExpiryDate!,
    isAnnouncementBannerDismissed,
  );

  // Get plugin-registered sidebar items
  const pluginSidebarItems = useMemo(
    () => pluginRegistry.getSidebarItems(),
    [],
  );

  const menuItems = useMemo<MenuItemType[]>(() => {
    const coreItems = getCoreSidebarItems(open);
    return mergePluginSidebarItems(coreItems, pluginSidebarItems);
  }, [open, pluginSidebarItems]);

  return (
    <Sidebar
      menuItems={menuItems}
      customLogo={customLogo}
      apiVersion={apiVersion}
      releaseVersion={releaseVersion}
      open={open}
      toggleMenu={toggleMenu}
      isMobile={isMobile}
      isAnnouncementBannerVisible={
        (showBanner && isBannerOpen) || showAiStudioBanner
      }
      onSearchClick={() => setSearchModal(true)}
    />
  );
};

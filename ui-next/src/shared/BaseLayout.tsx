/**
 * BaseLayout — the OSS sidebar + top bar layout with no enterprise dependencies.
 *
 * The enterprise `additional` plugin replaces this with an agent-aware wrapper
 * by registering an `appLayout` component via the plugin registry.
 */

import { AppBar, Box, Toolbar } from "@mui/material";
import SearchEverythingModal from "components/searchWrapper/SearchWrapper";
import { SidebarContext } from "components/Sidebar/context/SidebarContext";
import AnnouncementBanner from "components/v1/layout/header/AnnouncementBanner";
import { ReactNode, useContext } from "react";
import { UISidebar } from "components/Sidebar/UiSidebar";
import { releaseVersion } from "utils/releaseVersion";
import AppBarModules from "../plugins/AppBarModules";
import { useAuth } from "./auth";

const apiVersion = localStorage.getItem("version");
const toolBarHeight = 60;

type Props = {
  children: ReactNode;
};

export const BaseLayout = ({ children }: Props) => {
  const {
    toggleMenu,
    isMobile,
    hideSideBar,
    isBannerOpen,
    setBannerOpen,
    isSearchModalOpen,
    setSearchModal,
    showAiStudioBanner,
    dismissAiStudioBanner,
  } = useContext(SidebarContext);
  const {
    isTrialExpired,
    trialExpiryDate,
    isAnnouncementBannerDismissed,
    dismissAnnouncementBanner,
  } = useAuth();

  return (
    <Box
      id="side-and-top-bars-layout"
      sx={{
        display: "grid",
        gridTemplateColumns: "auto 1fr",
        gridTemplateAreas: `
          "links links"
          "announcement announcement"
          "sidebar header"
          "sidebar content"
        `,
        gridTemplateRows: "auto auto auto minmax(0, 1fr)",
        height: "100vh",
        width: "100vw",
      }}
    >
      <AnnouncementBanner
        bannerOpen={isBannerOpen}
        setBannerOpen={setBannerOpen}
        isTrialExpired={isTrialExpired}
        trialExpiryDate={trialExpiryDate!}
        showAiStudioBanner={showAiStudioBanner}
        dismissAiStudioBanner={dismissAiStudioBanner!}
        isAnnouncementBannerDismissed={isAnnouncementBannerDismissed}
        onDismissAnnouncementBanner={dismissAnnouncementBanner}
        sx={{ gridArea: "announcement" }}
      />

      <Box
        sx={[
          {
            gridArea: "sidebar",
            height: "100%",
            overflowY: "auto",
            overflowX: "hidden",
          },
          isMobile && {
            width: 0,
          },
        ]}
        id="app-sidebar"
      >
        {hideSideBar ? null : (
          <UISidebar
            apiVersion={apiVersion || ""}
            releaseVersion={releaseVersion}
          />
        )}

        {isSearchModalOpen && (
          <SearchEverythingModal
            open={isSearchModalOpen}
            setOpen={setSearchModal}
          />
        )}
      </Box>

      {!isMobile ? null : (
        <Box sx={{ gridArea: "header", height: `${toolBarHeight}px` }}>
          <AppBar
            color="primary"
            sx={{
              position: "relative",
              margin: 0,
              padding: 0,
              top: 0,
              borderBottom: "solid rgba(73, 105, 228, 0.2) 1px",
            }}
            elevation={0}
          >
            <Toolbar
              sx={{
                width: "100%",
                height: `${toolBarHeight}px`,
                zIndex: 900,
                display: "flex",
                flexDirection: "row-reverse",
                justifyContent: "space-between",
                alignItems: "center",
              }}
              variant="dense"
            >
              <AppBarModules handleDrawBarOpen={toggleMenu} />
            </Toolbar>
          </AppBar>
        </Box>
      )}

      <Box
        sx={{
          gridArea: "content",
          height: "100%",
          overflow: "auto",
          width: "100%",
        }}
        id="main-content"
      >
        {children}
      </Box>
    </Box>
  );
};

export default BaseLayout;

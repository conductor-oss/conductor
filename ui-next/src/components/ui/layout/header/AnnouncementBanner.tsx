import HighlightOffOutlinedIcon from "@mui/icons-material/HighlightOffOutlined";
import Box, { BoxProps } from "@mui/material/Box";
import Link from "@mui/material/Link";
import MuiIconButton from "components/ui/buttons/MuiIconButton";
import { drawerWidthClose } from "components/providers/sidebar/constants";
import { colors } from "theme/tokens/variables";
import { useAnnouncementBanner } from "./bannerUtils";
import AnnouncementIcon from "components/icons/AnnouncementIcon";
import { Grid } from "@mui/material";
import Button from "components/ui/buttons/MuiButton";
import ChatIcon from "components/icons/ChatIcon";
import { openInNewTab } from "utils/helpers";
import UnlockIcon from "components/icons/UnlockIcon";
import BannerIcon from "images/svg/banner-icon.svg";

const TALK_TO_AN_EXPERT_URL = "https://orkes.io/talk-to-an-expert";

export interface AnnouncementBannerProps extends BoxProps {
  bannerOpen: boolean;
  setBannerOpen: (val: boolean) => void;
  trialExpiryDate: number | Date;
  isTrialExpired: boolean;
  showAiStudioBanner?: boolean;
  dismissAiStudioBanner: () => void;
  /** Whether the announcement banner has been dismissed */
  isAnnouncementBannerDismissed: boolean;
  /** Callback to dismiss the announcement banner */
  onDismissAnnouncementBanner: () => void;
}

export default function AnnouncementBanner({
  sx,
  bannerOpen,
  setBannerOpen,
  trialExpiryDate,
  isTrialExpired,
  showAiStudioBanner,
  dismissAiStudioBanner,
  isAnnouncementBannerDismissed,
  onDismissAnnouncementBanner,
  ...rest
}: AnnouncementBannerProps) {
  const { showBanner, daysToGo } = useAnnouncementBanner(
    isTrialExpired,
    trialExpiryDate!,
    isAnnouncementBannerDismissed,
  );

  const handleBannerDismiss = () => {
    setBannerOpen(false);
    onDismissAnnouncementBanner();
  };

  const renderAnnouncementText = () => {
    if (daysToGo === 0) {
      return `Trial ends today.`;
    }
    return `Trial ends in ${daysToGo} ${daysToGo === 1 ? "day" : "days"}.`;
  };

  if (isTrialExpired) {
    return (
      <Box
        id="announcement-banner"
        sx={{
          position: "relative",
          height: "44px",
          background: colors.trialExpiredBannerBg,
          fontSize: "14px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 1201,
          boxShadow: "0px 4px 4px 0px rgba(0, 0, 0, 0.09)",
          ...sx,
          "@media (max-width: 898px)": {
            zIndex: 0,
            py: 3,
            height: "auto",
          },
        }}
        {...rest}
      >
        <Grid
          container
          direction={"row"}
          padding={"0 15px"}
          alignItems={"center"}
          sx={{ width: "100%" }}
        >
          <Grid>
            <Grid
              container
              sx={{ width: "100%" }}
              direction="row"
              alignItems={"center"}
            >
              <AnnouncementIcon size={29} color={colors.primary} />
              <Box
                sx={{
                  color: colors.trailExpiredTextColor,
                  fontSize: "16px",
                  fontWeight: 700,
                  padding: "0 13px",
                }}
              >
                Trial has expired.
              </Box>
              <Box sx={{ color: colors.errorRed }}>
                Your trial has ended. Please contact sales or upgrade your
                cluster.
              </Box>
            </Grid>
          </Grid>
          <Grid ml={"auto"}>
            <Grid
              container
              sx={{ width: "100%" }}
              direction="row"
              alignItems={"center"}
            >
              <Button
                variant="text"
                startIcon={<ChatIcon color={colors.primary} />}
                onClick={() =>
                  openInNewTab(
                    "https://orkes.io/orkes-cloud-free-trial?utm_source=playground",
                  )
                }
              >
                Contact Sales
              </Button>
              <Button
                startIcon={<UnlockIcon />}
                onClick={() => openInNewTab(TALK_TO_AN_EXPERT_URL)}
              >
                Talk to an expert
              </Button>
            </Grid>
          </Grid>
        </Grid>
      </Box>
    );
  }
  if (bannerOpen && showBanner) {
    return (
      <Box
        id="announcement-banner"
        sx={{
          position: "relative",
          pr: 11,
          py: 3,
          background:
            "linear-gradient(277deg, #F95E73 0%, #724AF7 51.04%, #1AACFE 100%)",
          color: (theme) => theme.palette.background.paper,
          fontWeight: 700,
          fontSize: "14px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          "> a": {
            ml: 1,
            color: colors.sidebarBlacky,
          },
          ...sx,
          "@media (max-width: 598px)": {
            flexDirection: "column",
            pl: `${drawerWidthClose + 8}px`,
          },
        }}
        {...rest}
      >
        {renderAnnouncementText()}
        <>
          <Link
            href={"https://orkes.io/talk-to-an-expert"}
            target="_blank"
            rel="noopener noreferrer"
          >
            Contact us&ensp;
          </Link>
        </>
        to upgrade to Enterprise.
        <MuiIconButton
          sx={{ position: "absolute", color: colors.sidebarBlacky, right: 12 }}
          onClick={() => handleBannerDismiss()}
        >
          <HighlightOffOutlinedIcon />
        </MuiIconButton>
      </Box>
    );
  }

  if (showAiStudioBanner) {
    return (
      <Box
        id="ai-studio-banner"
        sx={{
          position: "relative",
          pr: 11,
          py: 3,
          background:
            "linear-gradient(277deg, #F95E73 0%, #724AF7 51.04%, #1AACFE 100%)",
          color: (theme) => theme.palette.background.paper,
          fontWeight: 700,
          fontSize: "14px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          "> a": {
            textDecoration: "underline",
            color: colors.white,
            ml: 1,
          },
          ...sx,
          "@media (max-width: 598px)": {
            flexDirection: "column",
            pl: `${drawerWidthClose + 8}px`,
          },
        }}
        {...rest}
      >
        <img src={BannerIcon} alt="banner" style={{ marginRight: 8 }} />
        Extra, extra,{" "}
        <Link
          href={"http://orkes.io/aistudio"}
          target="_blank"
          rel="noopener noreferrer"
        >
          read all about it
        </Link>
        ! AI agent creation is coming to Orkes; meaning endless possibilities.
        😘
        <></>
        <MuiIconButton
          sx={{ position: "absolute", color: colors.white, right: 12 }}
          onClick={dismissAiStudioBanner}
        >
          <HighlightOffOutlinedIcon />
        </MuiIconButton>
      </Box>
    );
  }
  return null;
}

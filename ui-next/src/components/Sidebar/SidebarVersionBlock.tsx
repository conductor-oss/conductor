import { Box, Typography, useTheme } from "@mui/material";
import { alpha } from "@mui/material/styles";
import ClipboardCopy from "components/ui/ClipboardCopy";
import { colors } from "theme/tokens/variables";
import { FEATURES, featureFlags } from "utils";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);

interface SidebarVersionBlockProps {
  open: boolean;
  conductorVersion: string;
  uiVersion: string;
}

/**
 * Shared version block for the sidebar footer (logo, version copy, copyright).
 * Used by SidebarFooter and by SidebarMenu when rendering a custom userFooter.
 */
export function SidebarVersionBlock({
  open,
  conductorVersion,
  uiVersion,
}: SidebarVersionBlockProps) {
  const theme = useTheme();

  return (
    <Box
      sx={{
        backgroundColor: alpha(theme.palette.action.hover, 0.05),
        borderRadius: 1,
        p: 2,
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          mb: 1,
        }}
      >
        <img
          width="50%"
          src={open ? "/conductorLogo.svg" : "/conductorLogoSmall.svg"}
          alt="Conductor"
        />
      </Box>

      {!isPlayground && (
        <Box
          sx={{
            display: "flex",
            justifyContent: "center",
            flexDirection: "column",
          }}
        >
          <Typography
            textAlign="center"
            fontWeight={400}
            fontSize="14px"
            color={theme.palette.text.primary}
          >
            Orkes Platform Version
          </Typography>

          <ClipboardCopy
            buttonId="copy-version-btn"
            value={`${conductorVersion} | ${uiVersion}`}
            sx={{
              justifyContent: "center",
            }}
          >
            <Typography fontSize="12px" color={theme.palette.text.secondary}>
              {`${conductorVersion} | ${uiVersion}`}
            </Typography>
          </ClipboardCopy>
        </Box>
      )}

      <Typography
        fontSize="10px"
        textAlign="center"
        sx={{ color: colors.sidebarGrey }}
      >
        © Orkes Inc | All rights reserved
      </Typography>
    </Box>
  );
}

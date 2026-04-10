import { LogoutOutlined } from "@mui/icons-material";
import {
  Avatar,
  Box,
  Button,
  IconButton,
  Tooltip,
  Typography,
  alpha,
  useTheme,
} from "@mui/material";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { SidebarVersionBlock } from "./SidebarVersionBlock";
import TokenIcon from "images/svg/token.svg";
import { getAccessToken } from "components/features/auth/tokenManagerJotai";
import { Auth0User } from "types/User";
import { FEATURES, featureFlags } from "utils";
import type { ReactNode } from "react";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);

interface SidebarFooterProps {
  open: boolean;
  isAuthenticated: boolean;
  isMobile: boolean;
  user: Auth0User | null;
  conductorUser: { id: string } | null;
  logOut?: () => void;
  conductorVersion: string;
  uiVersion: string;
  showCopyAlert: boolean;
  setShowCopyAlert: (show: boolean) => void;
  /** When provided (e.g. by enterprise), used for user/sign-out block; version still shown below. */
  customUserBlock?: ReactNode;
}

export const SidebarFooter = ({
  open,
  isAuthenticated,
  isMobile,
  user,
  conductorUser,
  logOut,
  conductorVersion,
  uiVersion,
  showCopyAlert,
  setShowCopyAlert,
  customUserBlock,
}: SidebarFooterProps) => {
  const theme = useTheme();

  if (customUserBlock != null) {
    return (
      <>
        {customUserBlock}
        {open && (
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, mx: 1 }}>
            <SidebarVersionBlock
              open={open}
              conductorVersion={conductorVersion}
              uiVersion={uiVersion}
            />
          </Box>
        )}
      </>
    );
  }

  return (
    <>
      {/* Footer with Signout Button when collapsed */}
      {!open && isAuthenticated && !isMobile && (
        <Box
          sx={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            mb: 2,
          }}
        >
          <Tooltip title="Sign out" arrow placement="right">
            <IconButton
              onClick={() => {
                if (logOut) {
                  logOut();
                }
              }}
              size="small"
              sx={{
                color: theme.palette.text.secondary,
                "&:hover": {
                  backgroundColor: alpha(theme.palette.action.hover, 0.08),
                  color: theme.palette.text.primary,
                },
              }}
            >
              <LogoutOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>
      )}

      {/* Footer with UserInfo and Version */}
      {open && (
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            gap: 2,
            mx: 1,
          }}
        >
          {/* User Info, Signout and Copy Token */}
          {isAuthenticated && (
            <Box
              sx={{
                display: "flex",
                flexDirection: "column",
                gap: 1.5,
                mb: 1,
                mt: 4,
                p: 2,
                pb: 2,
                borderRadius: 1,
                borderTop: `1px solid ${alpha(theme.palette.divider, 0.1)}`,
              }}
            >
              {/* User Avatar and Info */}
              <Box
                sx={{
                  display: "flex",
                  alignItems: "center",
                  gap: 1.5,
                }}
              >
                <Avatar
                  data-testid="user-avatar"
                  src={(user as Auth0User)?.picture}
                  sx={{
                    width: 36,
                    height: 36,
                    border: `2px solid ${alpha(theme.palette.divider, 0.1)}`,
                  }}
                />
                <Box
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    flex: 1,
                    minWidth: 0,
                  }}
                >
                  <Typography
                    fontSize="0.875rem"
                    fontWeight={500}
                    sx={{
                      color: theme.palette.text.primary,
                      lineHeight: 1.2,
                      mb: 0.25,
                    }}
                  >
                    {(user as Auth0User)?.given_name}
                  </Typography>
                  <Typography
                    fontSize="0.625rem"
                    sx={{
                      color: alpha(theme.palette.text.primary, 0.7),
                      wordBreak: "break-all",
                      whiteSpace: "normal",
                      lineHeight: 1.2,
                    }}
                  >
                    {conductorUser?.id}
                  </Typography>
                </Box>
                <Tooltip title="Sign out" arrow placement="top">
                  <IconButton
                    onClick={() => {
                      if (logOut) {
                        logOut();
                      }
                    }}
                    size="small"
                    sx={{
                      color: theme.palette.text.secondary,
                      ml: "auto",
                    }}
                  >
                    <LogoutOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Box>

              {/* Copy Token Button */}
              <Box
                sx={{
                  display: "flex",
                  alignItems: "center",
                  gap: 1.5,
                }}
              >
                {/* Spacer for avatar */}
                <Box sx={{ width: 36, flexShrink: 0 }} />
                {(() => {
                  const copyTokenButton = (
                    <Button
                      id="user-info-copy-token-btn"
                      onClick={() => {
                        setShowCopyAlert(true);
                        const accessToken = getAccessToken();
                        if (accessToken) {
                          navigator.clipboard.writeText(accessToken);
                        }
                      }}
                      variant="outlined"
                      size="small"
                      startIcon={
                        <img src={TokenIcon} alt="copyToken" width="14px" />
                      }
                      sx={{
                        flex: 1,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        gap: 0.75,
                        color: theme.palette.text.secondary,
                        fontSize: "0.75rem",
                        px: 1.5,
                        py: 0.75,
                        minHeight: "auto",
                        borderRadius: 1,
                        border: `1px solid ${alpha(theme.palette.divider, 0.2)}`,
                        backgroundColor: "transparent",
                        textTransform: "none",
                        fontWeight: 500,
                        boxShadow: "none",
                        transition: "all 0.2s ease-in-out",
                        "&:hover": {
                          backgroundColor: alpha(
                            theme.palette.action.hover,
                            0.08,
                          ),
                          borderColor: alpha(theme.palette.primary.main, 0.3),
                          color: theme.palette.primary.main,
                          boxShadow: "none",
                        },
                        "&:active": {
                          boxShadow: "none",
                        },
                      }}
                    >
                      Copy Token
                    </Button>
                  );

                  return isPlayground ? (
                    <Tooltip
                      title="Copy token to test the api docs"
                      arrow
                      placement="top"
                    >
                      {copyTokenButton}
                    </Tooltip>
                  ) : (
                    copyTokenButton
                  );
                })()}
                {/* Spacer for signout button */}
                <Box sx={{ width: 40, flexShrink: 0 }} />
              </Box>
            </Box>
          )}

          {showCopyAlert && (
            <SnackbarMessage
              id="copy-clipboard-popup"
              message="Copied to Clipboard"
              severity="success"
              onDismiss={() => setShowCopyAlert(false)}
              anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
            />
          )}

          <SidebarVersionBlock
            open={open}
            conductorVersion={conductorVersion}
            uiVersion={uiVersion}
          />
        </Box>
      )}
    </>
  );
};

import { LogoutOutlined } from "@mui/icons-material";
import { Avatar, Box, Typography, Tooltip } from "@mui/material";
import { useAuth } from "components/features/auth";
import { useCallback, useState } from "react";
import { colors } from "theme/tokens/variables";
import TokenIcon from "images/svg/token.svg";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { Auth0User } from "types/User";
import { featureFlags, FEATURES } from "utils/flags";
import { getAccessToken } from "components/features/auth/tokenManagerJotai";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);

const UserInfo = () => {
  const { user, logOut, conductorUser, isAuthenticated } = useAuth(); // Todo this should not be here since its in v1
  const [showCopyAlert, setShowCopyAlert] = useState(false);

  const handleLogout = useCallback(() => {
    if (logOut) {
      logOut();
    }
  }, [logOut]);

  const copyTokenButton = isAuthenticated ? (
    <Box
      id="user-info-copy-token-btn"
      onClick={() => {
        setShowCopyAlert(true);
        const accessToken = getAccessToken();
        if (accessToken) {
          navigator.clipboard.writeText(accessToken);
        }
      }}
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        my: 4,
        gap: 1,
        color: colors.black,
        cursor: "pointer",
        fontSize: "10px",
      }}
    >
      <img src={TokenIcon} alt="copyToken" width="15px" />
      Copy Token
    </Box>
  ) : (
    <div />
  );

  return (
    <Box>
      {showCopyAlert && (
        <SnackbarMessage
          id="copy-clipboard-popup"
          message="Copied to Clipboard"
          severity="success"
          onDismiss={() => setShowCopyAlert(false)}
          anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
        />
      )}
      {isAuthenticated ? (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 2,
            color: colors.black,
            my: 3,
          }}
        >
          <Avatar
            data-testid="user-avatar"
            src={(user as Auth0User | null)?.picture}
            sx={{ width: 30, height: 30 }}
          />
          <Box sx={{ display: "flex", flexDirection: "column" }}>
            <Typography>{(user as Auth0User | null)?.given_name}</Typography>
            <Typography
              fontSize={9}
              sx={{
                maxWidth: "125px",
                wordBreak: "break-all",
                whiteSpace: "normal",
              }}
            >
              {conductorUser?.id}
            </Typography>
          </Box>

          <LogoutOutlined onClick={handleLogout} sx={{ cursor: "pointer" }} />
        </Box>
      ) : null}
      {isPlayground ? (
        <Tooltip title="Copy token to test the api docs" arrow>
          {copyTokenButton}
        </Tooltip>
      ) : (
        copyTokenButton
      )}
    </Box>
  );
};

export default UserInfo;

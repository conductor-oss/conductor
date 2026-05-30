import { Box, Tooltip } from "@mui/material";
import { List } from "@phosphor-icons/react";
import { IconButton, NavLink } from "components";
import { useAuth } from "components/features/auth";
import AppLogo from "plugins/AppLogo";
import { FEATURES, featureFlags } from "utils";

export default function AppBarModules({
  handleDrawBarOpen,
}: {
  handleDrawBarOpen: () => void;
}) {
  const { user } = useAuth();

  if (!featureFlags.isEnabled(FEATURES.ACCESS_MANAGEMENT)) {
    return null;
  }

  return user ? (
    <>
      <Tooltip title={"Toggle menu display"}>
        <IconButton
          sx={{
            width: "50px",
            height: "50px",
            "& svg": {
              fontSize: "27px",
            },
          }}
          onClick={handleDrawBarOpen}
          size="small"
          color="primary"
        >
          <List />
        </IconButton>
      </Tooltip>

      <Box
        sx={{
          height: "100%",
        }}
      >
        <NavLink
          path="/"
          style={{
            display: "flex",
            alignItems: "center",
            height: "100%",
          }}
        >
          <AppLogo />
        </NavLink>
      </Box>
    </>
  ) : null;
}

import { Box, Tooltip } from "@mui/material";
import { List } from "@phosphor-icons/react";

import { useAuth } from "shared/auth";
import { FEATURES, featureFlags } from "utils";

import { IconButton, NavLink } from "components";
import AppLogo from "plugins/AppLogo";

export default function AppBarModules({ handleDrawBarOpen }) {
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
          sx={{
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

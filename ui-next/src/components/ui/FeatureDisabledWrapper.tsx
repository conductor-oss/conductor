import { Box } from "@mui/material";
import { ReactNode } from "react";
import { useLocation } from "react-router";
import FeatureDisabledComponent from "./FeatureDisabledComponent";
import { checkPathFlag } from "utils/checkPathFlag";
import { colors } from "theme/tokens/variables";

const textStyle = {
  fontSize: "14px",
  fontWeight: 700,
  color: colors.sidebarBlacky,
  a: {
    color: colors.primary,
    textDecoration: "none",
  },
};

const featureDisabled = (path: string) => {
  const flagValue = checkPathFlag(path);
  return flagValue ? false : true;
};

export function FeatureDisabledWrapper({
  featureDisabledCustomComponent,
  children,
}: {
  children: ReactNode;
  featureDisabledCustomComponent?: ReactNode;
}) {
  const { pathname } = useLocation();

  return (
    <Box>
      {featureDisabled(pathname) ? (
        featureDisabledCustomComponent ? (
          featureDisabledCustomComponent
        ) : (
          <FeatureDisabledComponent />
        )
      ) : (
        <Box>{children}</Box>
      )}
    </Box>
  );
}

export function FeatureDisabledHeader() {
  return (
    <Box sx={textStyle}>
      {"Your trial has ended. Please "}
      <a
        target="_blank"
        rel="noreferrer"
        href="https://orkes.io/talk-to-an-expert"
      >
        contact us
      </a>
      {" or "}
      <a
        target="_blank"
        rel="noreferrer"
        href="https://orkes.io/talk-to-an-expert"
      >
        upgrade your cluster
      </a>
      .
    </Box>
  );
}

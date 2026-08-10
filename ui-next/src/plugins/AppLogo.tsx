import { OpenedLogo } from "components/providers/sidebar/OpenedLogo";
import { useContext, useMemo } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { FEATURES, featureFlags } from "utils";

const customLogo = featureFlags.getValue(FEATURES.CUSTOM_LOGO_URL);

export default function AppLogo() {
  const { mode } = useContext(ColorModeContext);

  const imgSrc = useMemo(() => {
    if (mode === "light") {
      return "/orkes-logo-purple-2x.png";
    }

    return "/orkes-logo-purple-inverted-2x.png";
  }, [mode]);

  return customLogo ? (
    <OpenedLogo customLogo={customLogo} width="60%" pl={6} />
  ) : (
    <img
      src={imgSrc}
      alt="Orkes Conductor"
      style={{
        width: "140px",
        marginRight: 30,
      }}
    />
  );
}

import { Box } from "@mui/material";
import { ZoomControlsButton } from "components/ZoomControlsButton";
import HomeIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Home";
import MinusIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Minus";
import PlusIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Plus";
import FitToFrame from "shared/icons/FitToFrame";
import { colors } from "theme/tokens/variables";
import { MAX_ZOOM } from "./constants";

export function DiagramControls({
  zoom,
  onReset,
  onZoomIn,
  onZoomOut,
  onFit,
}: {
  zoom: number;
  onReset: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onFit: () => void;
}) {
  const border = `1px solid ${colors.lightGrey}`;
  const col = colors.greyText;
  return (
    <Box
      sx={{
        position: "absolute",
        top: 5,
        left: 5,
        borderRadius: "6px",
        boxShadow: "0px 4px 12px 0px #0000001F",
        backgroundColor: "#fff",
        display: "flex",
        userSelect: "none",
        zIndex: 100,
      }}
    >
      <ZoomControlsButton onClick={onReset} tooltip="Reset position">
        <HomeIcon color={col} />
      </ZoomControlsButton>
      <ZoomControlsButton
        style={{ borderLeft: border, borderRight: border, width: 60 }}
      >
        {Math.round(zoom * 100)}%
      </ZoomControlsButton>
      <ZoomControlsButton onClick={onZoomOut} tooltip="Zoom out">
        <MinusIcon color={col} />
      </ZoomControlsButton>
      <ZoomControlsButton
        onClick={onZoomIn}
        disabled={zoom >= MAX_ZOOM}
        tooltip="Zoom in"
        style={{ borderLeft: border }}
      >
        <PlusIcon color={col} />
      </ZoomControlsButton>
      <ZoomControlsButton
        onClick={onFit}
        tooltip="Fit to screen"
        aria-label="Fit to screen"
        style={{
          borderLeft: border,
          borderTopRightRadius: 5,
          borderBottomRightRadius: 5,
        }}
      >
        <FitToFrame color={col} />
      </ZoomControlsButton>
    </Box>
  );
}

import { ZoomControlsButton } from "./ZoomControlButton";
// import PrintOutlinedIcon from "@mui/icons-material/PrintOutlined";
import PrintOutlinedIcon from "@material-ui/icons/PrintOutlined";
import Home from "../../components/icons/Home";
import Minus from "../../components/icons/Minus";
import Plus from "../../components/icons/Plus";
import FitToFrame from "../../components/icons/FitToFrame";

export const MIN_ZOOM = 0.02;
export const MAX_ZOOM = 2;

export const ZoomControls = ({
  zoom,
  setZoom,
  resetPosition,
  fitToScreen,
  printScreen,
}) => {
  const zoomPercent = Math.round(zoom * 100);
  const borderColor = "#ECECEC";

  return (
    <div
      style={{
        position: "absolute",
        top: "5px",
        left: "5px",
        borderRadius: "6px",
        boxShadow: "0px 4px 12px 0px #0000001F",
        backgroundColor: "#ffffff",
        display: "flex",
        userSelect: "none",
        zIndex: 100,
      }}
    >
      <ZoomControlsButton
        id="reset-position-button"
        onClick={() => {
          resetPosition();
        }}
        tooltip="Reset position"
      >
        <Home color={"grey"} />
      </ZoomControlsButton>
      <ZoomControlsButton
        style={{
          color: "grey",
          borderLeft: `1px solid ${borderColor}`,
          borderRight: `1px solid ${borderColor}`,
          width: "60px",
        }}
      >
        {zoomPercent}%
      </ZoomControlsButton>
      <ZoomControlsButton
        id="zoom-out-button"
        data-cy="diagram-zoom-out"
        style={{
          color: "grey",
        }}
        onClick={() => {
          setZoom(true);
        }}
        tooltip="Zoom out"
      >
        <Minus color={"grey"} />
      </ZoomControlsButton>
      <ZoomControlsButton
        id="zoom-in-button"
        data-cy="diagram-zoom-in"
        onClick={() => {
          setZoom(false);
        }}
        style={{
          borderLeft: `1px solid ${borderColor}`,
        }}
        // disabled={isInconsistent || disableZoomIn}
        tooltip="Zoom in"
      >
        <Plus color={"grey"} />
      </ZoomControlsButton>
      <ZoomControlsButton
        id="fit-screen-button"
        style={{
          borderLeft: `1px solid ${borderColor}`,
        }}
        onClick={fitToScreen}
        tooltip="Fit to screen"
      >
        <FitToFrame color={"grey"} />
      </ZoomControlsButton>

      <ZoomControlsButton
        id="print-screen-button"
        style={{
          borderLeft: `1px solid ${borderColor}`,
        }}
        onClick={() => {
          printScreen();
        }}
        tooltip="Export to image"
      >
        <PrintOutlinedIcon color={"grey"} />
      </ZoomControlsButton>
    </div>
  );
};

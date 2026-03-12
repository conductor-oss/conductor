import { useCallback, useRef, useState } from "react";
import { createTheme, useTheme, ThemeProvider } from "@mui/material";
import { colors } from "theme/tokens/variables";

import getTheme from "theme/theme";
import { Box } from "@mui/material";
import useMediaQuery from "@mui/material/useMediaQuery";

const MIN_LEFT_WIDTH = 400;
const MIN_RIGHT_WIDTH = 150;
const SMALL_PERCENT_THREASHOLD = 34;

const smallThemeCreate = (
  _existingTheme, // ignore existingTheme for now. since it will make font bigger when not on mobile
) =>
  createTheme({
    ...getTheme(),
    breakpoints: {
      values: {
        xs: 0,
        sm: 20,
      },
    },
  });

const TwoPanesBoxider = ({
  leftPanelContent,
  rightPanelContent,
  leftPanelExpanded = false,
  setLeftPanelExpanded,
}) => {
  const theme = useTheme();
  // Checking responsive width
  const isValidWidth = useMediaQuery((theme) => theme.breakpoints.down("sm"));

  const [isHoveringResizer, setIsHoveringResizer] = useState(false);
  const [rightPanelTheme, setRightPanelTheme] = useState({
    theme,
    name: "default",
  });

  const containerRef = useRef(null);
  const leftPanelRef = useRef(null);
  const rightPanelRef = useRef(null);
  const resizerRef = useRef(null);

  const handleMouseDown = () => {
    document.addEventListener("mouseup", handleMouseUp, true);
    document.addEventListener("mousemove", handleMouseMove, true);
  };

  const handleMouseUp = () => {
    document.removeEventListener("mouseup", handleMouseUp, true);
    document.removeEventListener("mousemove", handleMouseMove, true);
  };

  const handleMouseMove = useCallback(
    (e) => {
      e.preventDefault();

      const boundingClientRect = leftPanelRef.current.getBoundingClientRect();

      const leftWidth = e.clientX - boundingClientRect.x;
      const containerWidth = containerRef.current.offsetWidth;
      const rightWidth = containerWidth - leftWidth;

      const leftWidthAsPercent = (leftWidth / containerWidth) * 100;
      const rightWidthAsPercent = (rightWidth / containerWidth) * 100;

      if (leftWidth >= MIN_LEFT_WIDTH && rightWidth >= MIN_RIGHT_WIDTH) {
        leftPanelRef.current.style.width = `${leftWidthAsPercent}%`;
        rightPanelRef.current.style.width = `${rightWidthAsPercent}%`;
        resizerRef.current.style.left = `calc(${leftWidthAsPercent}% - 3px)`;
      }

      const isNotMobileAndRightPanelIsSmall =
        !isValidWidth && SMALL_PERCENT_THREASHOLD > rightWidthAsPercent;

      if (isNotMobileAndRightPanelIsSmall) {
        setRightPanelTheme({ theme: smallThemeCreate(theme), name: "small" });
      } else {
        setRightPanelTheme({ theme, name: "default" });
      }
    },
    [theme, isValidWidth],
  );

  return (
    <Box
      sx={{
        display: "block",
        width: "100%",
        position: "relative",
      }}
      ref={containerRef}
    >
      <Box
        sx={{
          width: isValidWidth ? "80%" : "50%",
          zIndex: 0,
          minWidth: leftPanelExpanded
            ? "100%"
            : isValidWidth
              ? "80%"
              : MIN_LEFT_WIDTH,
          height: "100%",
          position: "absolute",
        }}
        ref={leftPanelRef}
      >
        <Box
          onClick={() => setLeftPanelExpanded(!leftPanelExpanded)}
          sx={{
            display: [leftPanelExpanded ? "none" : "block", "none"],
            width: "100%",
            height: "100%",
            background: "black",
            opacity: 0.4,
            position: "absolute",
            top: 0,
            left: 0,
            zIndex: 999,
          }}
        ></Box>

        {leftPanelContent}
      </Box>

      <Box
        sx={{
          display: leftPanelExpanded ? "none" : "block",
          position: ["absolute"],
          overflow: "hidden",
          width: ["90%", "50%"],
          right: 0,
          height: "100%",
          // stronger shadow on mobile
          boxShadow: [
            "-5px 0px 20px rgba(0, 0, 0, .8)",
            "0px 0px 6px rgba(0, 0, 0, 0.18)",
          ],
          background: "white",
        }}
        ref={rightPanelRef}
      >
        <Box
          sx={{
            width: "100%",
            height: "100%",
            overflow: "hidden",
          }}
        >
          <Box
            sx={{
              minWidth: MIN_RIGHT_WIDTH,
              height: "100%",
              overflow: "hidden",
            }}
          >
            <ThemeProvider theme={rightPanelTheme.theme}>
              {rightPanelContent}
            </ThemeProvider>
          </Box>
        </Box>
      </Box>

      <Box
        ref={resizerRef}
        onMouseDown={(e) => handleMouseDown(e)}
        onMouseEnter={(_e) => setIsHoveringResizer(true)}
        onMouseLeave={(_e) => setIsHoveringResizer(false)}
        id="editor-panel-resize-line"
        sx={{
          position: "absolute",
          left: "50%",
          height: "100%",
          width: "8px",
          marginLeft: "-4px",
          cursor: "col-resize",
          backgroundColor: colors.primary,
          opacity: isHoveringResizer ? 1 : 0,
          transition: "opacity 0.10s ease-in-out",
          zIndex: 5,
          flexShrink: 0,
          resize: "horizontal",
          display: ["none", leftPanelExpanded ? "none" : "block"],
        }}
      />
    </Box>
  );
};

export default TwoPanesBoxider;

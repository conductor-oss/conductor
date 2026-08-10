import { Box, createTheme, ThemeProvider, useTheme } from "@mui/material";
import useMediaQuery from "@mui/material/useMediaQuery";
import {
  useCallback,
  useRef,
  useState,
  type Dispatch,
  type ReactNode,
  type SetStateAction,
} from "react";
import getTheme from "theme/theme";
import { colors } from "theme/tokens/variables";

const MIN_LEFT_WIDTH = 400;
const MIN_RIGHT_WIDTH = 150;
const SMALL_PERCENT_THREASHOLD = 34;

// Base theme from getTheme(), not useTheme(): using the live theme here increased font sizes off-mobile.
const smallThemeCreate = () => {
  const base = getTheme();
  return createTheme({
    ...base,
    breakpoints: {
      ...base.breakpoints,
      values: {
        ...base.breakpoints.values,
        xs: 0,
        sm: 20,
      },
    },
  });
};

export type TwoPanesDividerProps = {
  leftPanelContent: ReactNode;
  rightPanelContent: ReactNode;
  leftPanelExpanded?: boolean;
  setLeftPanelExpanded: Dispatch<SetStateAction<boolean>>;
  hideCollapseButton?: boolean;
};

const TwoPanesDivider = ({
  leftPanelContent,
  rightPanelContent,
  leftPanelExpanded = false,
  setLeftPanelExpanded,
  hideCollapseButton: _hideCollapseButton,
}: TwoPanesDividerProps) => {
  const theme = useTheme();
  // Checking responsive width
  const isValidWidth = useMediaQuery((theme) => theme.breakpoints.down("sm"));

  const [isHoveringResizer, setIsHoveringResizer] = useState(false);
  const [rightPanelTheme, setRightPanelTheme] = useState({
    theme,
    name: "default",
  });

  const containerRef = useRef<HTMLDivElement | null>(null);
  const leftPanelRef = useRef<HTMLDivElement | null>(null);
  const rightPanelRef = useRef<HTMLDivElement | null>(null);
  const resizerRef = useRef<HTMLDivElement | null>(null);

  const handleMouseDown = () => {
    document.addEventListener("mouseup", handleMouseUp, true);
    document.addEventListener("mousemove", handleMouseMove, true);
  };

  const handleMouseUp = () => {
    document.removeEventListener("mouseup", handleMouseUp, true);
    document.removeEventListener("mousemove", handleMouseMove, true);
  };

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      e.preventDefault();

      const leftEl = leftPanelRef.current;
      const containerEl = containerRef.current;
      const rightEl = rightPanelRef.current;
      const resizerEl = resizerRef.current;
      if (!leftEl || !containerEl || !rightEl || !resizerEl) {
        return;
      }

      const boundingClientRect = leftEl.getBoundingClientRect();

      const leftWidth = e.clientX - boundingClientRect.x;
      const containerWidth = containerEl.offsetWidth;
      const rightWidth = containerWidth - leftWidth;

      const leftWidthAsPercent = (leftWidth / containerWidth) * 100;
      const rightWidthAsPercent = (rightWidth / containerWidth) * 100;

      if (leftWidth >= MIN_LEFT_WIDTH && rightWidth >= MIN_RIGHT_WIDTH) {
        leftEl.style.width = `${leftWidthAsPercent}%`;
        rightEl.style.width = `${rightWidthAsPercent}%`;
        resizerEl.style.left = `calc(${leftWidthAsPercent}% - 3px)`;
      }

      const isNotMobileAndRightPanelIsSmall =
        !isValidWidth && SMALL_PERCENT_THREASHOLD > rightWidthAsPercent;

      if (isNotMobileAndRightPanelIsSmall) {
        setRightPanelTheme({ theme: smallThemeCreate(), name: "small" });
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
        onMouseDown={handleMouseDown}
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

export default TwoPanesDivider;

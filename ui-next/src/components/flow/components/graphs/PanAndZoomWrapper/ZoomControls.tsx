import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import PrintOutlinedIcon from "@mui/icons-material/PrintOutlined";
import { Box, Button, Stack } from "@mui/material";
import { useSelector } from "@xstate/react";
import { FlowActionTypes, FlowEvents } from "components/flow/state";
import CustomTooltip from "pages/definition/EditorPanel/CustomTooltip";
import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
  WorkflowEditContext,
} from "pages/definition/state";
import {
  FunctionComponent,
  RefObject,
  useCallback,
  useContext,
  useRef,
} from "react";
import FitToFrame from "shared/icons/FitToFrame";
import { ZoomControlsButton } from "shared/ZoomControlsButton";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import { logrocketTrackIfEnabled } from "utils/logrocket";
import { ActorRef } from "xstate";
import { MAX_ZOOM } from "./constants";
import DragNDrop from "./icons/DragNDrop";
import Home from "./icons/Home";
import Minus from "./icons/Minus";
import Plus from "./icons/Plus";
import Search from "./icons/Search";
import { SearchBox } from "./SearchBox";

export interface ZoomControlsProps {
  zoom: number;
  setZoom: (zoomIn: boolean) => void;
  resetPosition: () => void;
  isInconsistent: boolean;
  fitToScreen: () => void;
  togglePan: () => void;
  panEnabled: boolean;
  flowActor: ActorRef<FlowEvents>;
  isSearchFieldVisible: boolean;
  toggleSearchField: () => void;
  printScreen: () => void;
  isExecutionView: boolean;
}
// FIXME this should not be here since we are coupling to the definition machine..
// ONCE dillip confirms move it elsewhere.
const MaybeCoolTooltip = ({
  actor,
  anchorEl,
}: {
  actor: ActorRef<WorkflowDefinitionEvents>;
  anchorEl: RefObject<HTMLButtonElement | null>;
}) => {
  const shouldShowTooltip = useSelector(actor, (state) =>
    state.hasTag("showDescriptionTooltip"),
  );
  const handleNextButtonClick = useCallback(() => {
    actor.send({
      type: DefinitionMachineEventTypes.NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG,
    });
  }, [actor]);

  const handleDismissTutorial = () => {
    actor.send(DefinitionMachineEventTypes.DISMISS_IMPORT_SUCCESSFUL_DIALOG);
  };

  return shouldShowTooltip ? (
    <CustomTooltip
      open={shouldShowTooltip}
      anchorEl={anchorEl.current}
      onClose={handleDismissTutorial}
      content={
        <Stack spacing={2}>
          <Box
            sx={{
              mb: 1,
              display: "flex",
              alignItems: "center",
              gap: 1,
            }}
          >
            <Box component="span" sx={{ fontSize: "14px", fontWeight: 600 }}>
              Diagram controls
            </Box>
          </Box>
          <Box sx={{ color: "#252525" }}>
            Use diagram controls to show task descriptions, Change zoom
            settings, Drag tasks arround and more.
          </Box>
          <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
            <Button
              onClick={handleNextButtonClick}
              size="small"
              id="btn-diagram-controls-done"
            >
              Done
            </Button>
          </Box>
        </Stack>
      }
    />
  ) : null;
};

export const ZoomControls: FunctionComponent<ZoomControlsProps> = ({
  zoom,
  setZoom,
  resetPosition,
  isInconsistent,
  fitToScreen,
  togglePan,
  panEnabled,
  flowActor,
  isSearchFieldVisible,
  toggleSearchField,
  printScreen,
  isExecutionView,
}) => {
  const { mode } = useContext(ColorModeContext);
  const workflowEditContext = useContext(WorkflowEditContext);
  const darkMode = mode === "dark";
  const zoomPercent = Math.round(zoom * 100);
  const borderColor = darkMode ? colors.gray04 : colors.lightGrey;

  const anchorRef = useRef<HTMLDivElement>(null);
  const showDescriptionButtonRef = useRef<HTMLButtonElement | null>(null);
  const disableZoomIn = zoom >= MAX_ZOOM;

  const isShowDescription = useSelector(flowActor, (state) =>
    state.hasTag("showDescription"),
  );
  const handleToggleShowDescription = useCallback(() => {
    flowActor.send({ type: FlowActionTypes.TOGGLE_SHOW_DESCRIPTION });
    logrocketTrackIfEnabled("user_toggle_show_description");
  }, [flowActor]);

  return (
    <Box
      sx={{
        position: "absolute",
        top: "5px",
        left: "5px",
        borderRadius: "6px",
        boxShadow: "0px 4px 12px 0px #0000001F",
        backgroundColor: darkMode ? colors.black : colors.white,
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
        disabled={isInconsistent}
        tooltip="Reset position"
      >
        <Home color={colors.greyText} />
      </ZoomControlsButton>
      <ZoomControlsButton
        style={{
          color: darkMode ? colors.gray12 : colors.greyText,
          borderLeft: `1px solid ${borderColor}`,
          borderRight: `1px solid ${borderColor}`,
          width: "60px",
        }}
        disabled={isInconsistent}
      >
        {zoomPercent}%
      </ZoomControlsButton>
      <ZoomControlsButton
        id="zoom-out-button"
        data-cy="diagram-zoom-out"
        style={{
          color: darkMode ? colors.gray12 : colors.gray06,
        }}
        onClick={() => {
          setZoom(true);
        }}
        disabled={isInconsistent}
        tooltip="Zoom out"
      >
        <Minus color={colors.greyText} />
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
        disabled={isInconsistent || disableZoomIn}
        tooltip="Zoom in"
      >
        <Plus color={colors.greyText} />
      </ZoomControlsButton>
      <ZoomControlsButton
        id="fit-screen-button"
        style={{
          borderLeft: `1px solid ${borderColor}`,
        }}
        onClick={fitToScreen}
        disabled={isInconsistent}
        tooltip="Fit to screen"
      >
        <FitToFrame color={colors.greyText} />
      </ZoomControlsButton>
      {!isExecutionView && (
        <ZoomControlsButton
          id="toggle-pan-button"
          style={{
            borderLeft: `1px solid ${borderColor}`,
            ...(!panEnabled
              ? {
                  boxShadow: "0px 4px 12px 0px #0000001F inset",
                  background: colors.blueLightMode,
                }
              : {}),
          }}
          onClick={() => togglePan()}
          disabled={isInconsistent}
          tooltip={`${panEnabled ? "Enable" : "Disable"} dragging mode`}
        >
          <DragNDrop color={!panEnabled ? colors.black : colors.greyText} />
        </ZoomControlsButton>
      )}
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
        <PrintOutlinedIcon />
      </ZoomControlsButton>
      <ZoomControlsButton
        ref={showDescriptionButtonRef}
        id="show-description-button"
        style={{
          borderLeft: `1px solid ${borderColor}`,
          ...(isShowDescription
            ? {
                boxShadow: "0px 4px 12px 0px #0000001F inset",
                background: colors.blueLightMode,
                color: "white",
              }
            : {}),
        }}
        onClick={handleToggleShowDescription}
        tooltip="Show description"
      >
        <HelpOutlineIcon />
      </ZoomControlsButton>

      <Box ref={anchorRef}>
        <ZoomControlsButton
          id="search-task-button"
          style={{
            borderTopRightRadius: "5px",
            borderBottomRightRadius: "5px",
            borderLeft: `1px solid ${borderColor}`,
            ...(isSearchFieldVisible && {
              boxShadow: "0px 4px 12px 0px #0000001F inset",
              background: colors.blueLightMode,
            }),
          }}
          onClick={toggleSearchField}
          tooltip="Search task"
        >
          <Search
            color={isSearchFieldVisible ? colors.black : colors.greyText}
          />
        </ZoomControlsButton>
      </Box>
      {isSearchFieldVisible && (
        <SearchBox flowActor={flowActor} anchorEl={anchorRef} />
      )}
      {workflowEditContext.workflowDefinitionActor != null ? (
        <MaybeCoolTooltip
          actor={workflowEditContext.workflowDefinitionActor}
          anchorEl={showDescriptionButtonRef}
        />
      ) : null}
    </Box>
  );
};

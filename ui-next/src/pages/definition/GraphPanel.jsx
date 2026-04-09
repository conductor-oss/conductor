import { useCallback, useState } from "react";
import { Box } from "@mui/material";
import { useSelector } from "@xstate/react";
import { Flow } from "components/features/flow/Flow";
import { useLocalCopyMachine } from "./ConfirmLocalCopyDialog/state/hook";
import { useMemo } from "react";
import ProgressIcon from "./progressicons";
import { X } from "@phosphor-icons/react";
import { DefinitionMachineEventTypes } from "pages/definition/state/types";
import MuiAlert from "components/ui/MuiAlert";
import { usePanelChanges } from "pages/definition/state/usePanelChanges";
import { selectIsOpenedEdge } from "components/features/flow/state/selectors";
import AddTaskSidebar from "components/features/flow/components/RichAddTaskMenu/AddTaskSidebar";

const GraphPanel = ({ definitionActor }) => {
  const { leftPanelExpanded, setLeftPanelExpanded } =
    usePanelChanges(definitionActor);
  const localCopyMessage = useSelector(
    definitionActor,
    (state) => state.context.localCopyMessage,
  );
  const flowActor = definitionActor.children?.get("flowMachine");
  const [isHovered, setIsHovered] = useState(false);

  const openedEdge = useSelector(flowActor, selectIsOpenedEdge);

  const richAddTaskMenuActor = flowActor?.children.get(
    "richAddTaskMenuMachine",
  );

  const menuType = useSelector(richAddTaskMenuActor || flowActor, (state) =>
    richAddTaskMenuActor ? state.context.menuType : undefined,
  );

  const [{ handleRemoveLocalCopyMessage }] =
    useLocalCopyMachine(definitionActor);
  const handleResetRequest = useCallback(() => {
    definitionActor.send({ type: DefinitionMachineEventTypes.RESET_EVT });
  }, [definitionActor]);

  const linkStyle = useMemo(
    () => ({
      cursor: "pointer",
      color: isHovered ? "#13599e" : "#1976d2",
      padding: "0 3px",
    }),
    [isHovered],
  );
  const localCopyAlert = useMemo(
    () => (
      <label style={{ display: "flex", flexWrap: "wrap" }}>
        {localCopyMessage}
        <span style={{ display: "flex", padding: "0 3px" }}>
          Click
          <div
            onClick={handleResetRequest}
            style={linkStyle}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
          >
            'Reset'
          </div>
          to start new.
        </span>
      </label>
    ),
    [handleResetRequest, localCopyMessage, linkStyle],
  );
  return (
    <Box
      style={{
        overflow: "hidden",
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
      }}
    >
      {localCopyMessage ? (
        <MuiAlert
          severity="info"
          action={
            <>
              <Box sx={{ marginTop: "-3px" }}>
                <ProgressIcon />
              </Box>
              <X
                size={20}
                style={{ cursor: "pointer" }}
                onClick={() => handleRemoveLocalCopyMessage()}
              />
            </>
          }
        >
          {localCopyAlert}
        </MuiAlert>
      ) : null}

      {flowActor &&
        (openedEdge && menuType === "advanced" ? (
          <Box sx={{ display: "flex", flex: 1, overflow: "hidden" }}>
            <Box sx={{ flex: 1, minWidth: 0, height: "100%" }}>
              <Flow
                flowActor={flowActor}
                leftPanelExpanded={leftPanelExpanded}
                setLeftPanelExpanded={setLeftPanelExpanded}
              />
            </Box>

            <AddTaskSidebar
              open={openedEdge}
              richAddTaskMenuActor={flowActor?.children.get(
                "richAddTaskMenuMachine",
              )}
            />
          </Box>
        ) : (
          <Flow
            flowActor={flowActor}
            leftPanelExpanded={leftPanelExpanded}
            setLeftPanelExpanded={setLeftPanelExpanded}
          />
        ))}
    </Box>
  );
};

export default GraphPanel;

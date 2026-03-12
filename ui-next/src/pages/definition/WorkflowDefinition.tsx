import { AlertColor, Box } from "@mui/material";
import { useSelector } from "@xstate/react";
import { SnackbarMessage } from "components/SnackbarMessage";
import TwoPanesDivider from "components/TwoPanesDivider";
import {
  DefinitionMachineContext,
  FlowEditContextProvider,
  WorkflowDefinitionEvents,
} from "pages/definition/state";
import { Helmet } from "react-helmet";
import { useAuth } from "shared/auth";
import { ActorRef, State } from "xstate";
import sharedStyles from "../styles";
import EditorPanel from "./EditorPanel/EditorPanel";
import GraphPanel from "./GraphPanel";
import { PromptIfChanges } from "./PromptIfChanges";
import { useWorkflowDefinition } from "./state/hook";
import { WorkflowMetaBar } from "./WorkflowMetadata";

export default function Workflow() {
  const { conductorUser } = useAuth();
  const [
    { handleResetMessage, setLeftPanelExpanded },
    { workflowName, message, definitionActor, leftPanelExpanded },
  ] = useWorkflowDefinition(conductorUser!);

  const graphPanel = <GraphPanel definitionActor={definitionActor} />;

  const editorPanel = definitionActor && (
    <EditorPanel definitionActor={definitionActor} />
  );

  const isReady = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) => state.matches("ready"),
  );

  return (
    <>
      {isReady && definitionActor && (
        <WorkflowMetaBar
          {...{
            definitionActor: definitionActor,
            leftPanelExpanded,
            setLeftPanelExpanded,
          }}
        />
      )}
      <Box sx={sharedStyles.wrapper}>
        <Helmet>
          <title>Workflow Definition - {workflowName || "NEW"}</title>
        </Helmet>
        <SnackbarMessage
          message={message?.text as string}
          severity={message?.severity as AlertColor}
          onDismiss={handleResetMessage}
        />

        <Box
          sx={{
            height: "100%",
            flex: "1 1 0%",
            position: "relative",
          }}
          data-testid="workflow-definition-container"
        >
          <Box
            sx={{
              height: "100%",
              width: "100%",
              overflow: "visible",
              display: "flex",
              flexDirection: "column",
              backgroundColor: "transparent",
              fontSize: "13px",
              position: "relative",
              zIndex: 1,
            }}
          >
            <Box
              sx={{
                display: "flex",
                height: "100%",
                position: "absolute",
                overflow: "visible",
                userSelect: "text",
                flexDirection: "row",
                left: "0px",
                right: "0px",
              }}
            >
              {/* {showImportSuccessfulDialog && fetchingWfSuccessful && (
                <FloatingMuiAlert
                  title="Congratulations! You've created a workflow!"
                  message="Edit whatever you want, or not, and take it for a Run!"
                  onClose={hideExportSuccessModal}
                />
              )} */}
              <PromptIfChanges
                definitionActor={
                  definitionActor as ActorRef<WorkflowDefinitionEvents>
                }
              />

              <FlowEditContextProvider
                workflowDefinitionActor={definitionActor}
              >
                {definitionActor?.children.get("flowMachine") && (
                  <TwoPanesDivider
                    leftPanelContent={graphPanel}
                    rightPanelContent={editorPanel}
                    leftPanelExpanded={leftPanelExpanded}
                    setLeftPanelExpanded={setLeftPanelExpanded}
                  />
                )}
              </FlowEditContextProvider>
            </Box>
          </Box>
        </Box>
      </Box>
    </>
  );
}

import { AlertColor, Box, LinearProgress } from "@mui/material";
import { useSelector } from "@xstate/react";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import Error from "components/ui/Error";
import MuiTypography from "components/ui/MuiTypography";
import TwoPanesDivider from "components/ui/TwoPanesDivider";
import {
  DefinitionMachineContext,
  FlowEditContextProvider,
  WorkflowDefinitionEvents,
} from "pages/definition/state";
import { Helmet } from "react-helmet";
import { useAuth } from "components/features/auth";
import { useMemo } from "react";
import { useNavigate } from "react-router";
import { WORKFLOW_DEFINITION_URL } from "utils/constants/route";
import { ActorRef, State } from "xstate";
import sharedStyles from "../styles";
import EditorPanel from "./EditorPanel/EditorPanel";
import GraphPanel from "./GraphPanel";
import { PromptIfChanges } from "./PromptIfChanges";
import { useWorkflowDefinition } from "./state/hook";
import { WorkflowMetaBar } from "./WorkflowMetadata";

export default function Workflow() {
  const { conductorUser } = useAuth();
  const navigate = useNavigate();
  const [
    { handleResetMessage, setLeftPanelExpanded },
    {
      workflowName,
      workflowVersions,
      currentVersion,
      message,
      definitionActor,
      leftPanelExpanded,
      isNotFound,
      isErrorFetching,
    },
  ] = useWorkflowDefinition(conductorUser!);

  const graphPanel = <GraphPanel definitionActor={definitionActor} />;

  const editorPanel = definitionActor && (
    <EditorPanel definitionActor={definitionActor} />
  );

  const isReady = useSelector(
    definitionActor,
    (state: State<DefinitionMachineContext>) => state.matches("ready"),
  );

  const latestVersion = useMemo(() => {
    if (!workflowVersions?.length) return undefined;
    return Math.max(...workflowVersions.map(Number));
  }, [workflowVersions]);

  if (isNotFound || isErrorFetching) {
    const description = isNotFound
      ? latestVersion != null
        ? `Version ${currentVersion} of "${workflowName}" was not found. The latest available version is ${latestVersion}.`
        : currentVersion
          ? `Version ${currentVersion} of "${workflowName}" was not found.`
          : `Workflow "${workflowName}" was not found.`
      : message?.text || "Failed to load this workflow definition.";

    const openLatest = () => {
      if (workflowName && latestVersion != null) {
        navigate(
          `${WORKFLOW_DEFINITION_URL.BASE}/${encodeURIComponent(
            workflowName,
          )}/${latestVersion}`,
        );
      } else {
        navigate(WORKFLOW_DEFINITION_URL.BASE);
      }
    };

    const canOpenLatest = isNotFound && latestVersion != null;

    return (
      <Box
        sx={{
          height: "100%",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          p: 5,
        }}
        data-testid="workflow-definition-not-found"
      >
        <Helmet>
          <title>
            {isNotFound ? "Version Not Found" : "Error"} -{" "}
            {workflowName || "Workflow"}
          </title>
        </Helmet>
        <SnackbarMessage
          message={message?.text as string}
          severity={(message?.severity as AlertColor) || "error"}
          onDismiss={handleResetMessage}
        />
        <MuiTypography fontSize={20} fontWeight={700} mb={8}>
          {isNotFound ? "VERSION NOT FOUND" : "ERROR"}
        </MuiTypography>
        <Error
          title={isNotFound ? "404" : "Error"}
          description={description}
          buttonText={
            canOpenLatest
              ? `OPEN LATEST VERSION (v${latestVersion})`
              : "BACK TO WORKFLOW DEFINITIONS"
          }
          onClick={
            canOpenLatest
              ? openLatest
              : () => navigate(WORKFLOW_DEFINITION_URL.BASE)
          }
        />
      </Box>
    );
  }

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
          {!isReady && <LinearProgress />}
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
              <PromptIfChanges
                definitionActor={
                  definitionActor as ActorRef<WorkflowDefinitionEvents>
                }
              />

              <FlowEditContextProvider
                workflowDefinitionActor={definitionActor}
              >
                {definitionActor?.children.get("flowMachine") && isReady && (
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

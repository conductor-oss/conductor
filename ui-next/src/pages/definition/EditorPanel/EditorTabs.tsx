import { Badge, Box, Button, Stack } from "@mui/material";
import { Tab, Tabs } from "components";
import IconButton from "components/ui/buttons/MuiIconButton";
import DoubleArrowRightIcon from "components/icons/DoubleArrowRightIcon";
import React, { forwardRef, useRef } from "react";
import { FEATURES, featureFlags } from "utils";
import { ActorRef, EventObject } from "xstate";
import {
  CODE_TAB,
  DEPENDENCIES_TAB,
  RUN_TAB,
  TASK_TAB,
  WORKFLOW_TAB,
} from "../state/constants";
import { WorkflowDefinitionEvents } from "../state/types";
import CustomTooltip from "./CustomTooltip";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);

// Type helper for ActorRef with children property
type ActorRefWithChildren<T extends EventObject> = ActorRef<T> & {
  children?: {
    get: <E extends EventObject = EventObject>(
      id: string,
    ) => ActorRef<E> | undefined;
  };
};

const WorkflowTabContent = forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>((props, ref) => {
  return (
    <div ref={ref} {...props}>
      Workflow
    </div>
  );
});

interface EditorTabsProps {
  openedTab: number;
  definitionActor: ActorRef<WorkflowDefinitionEvents>;
  changeTab: (tab: number) => void;
  setLeftPanelExpanded: () => void;
  isFirstTimeFlowWorkflowDialog: boolean;
  isShowRunMessageDialog: boolean;
  isShowDependenciesDialog: boolean;
  handleNextButtonClick: () => void;
  handleDismissTutorial: () => void;
  tabsContainerRef: React.RefObject<HTMLDivElement | null>;
}

export const EditorTabs = ({
  openedTab,
  definitionActor,
  changeTab,
  setLeftPanelExpanded,
  isFirstTimeFlowWorkflowDialog,
  isShowRunMessageDialog,
  isShowDependenciesDialog,
  handleNextButtonClick,
  handleDismissTutorial,
  tabsContainerRef,
}: EditorTabsProps) => {
  const workflowTabRef = useRef(null);
  const runTabRef = useRef(null);
  const dependenciesTabRef = useRef(null);

  return (
    <Box
      ref={tabsContainerRef}
      sx={{
        display: "flex",
        background: "#ffffff",
        borderBottom: "1px solid rgba(0,0,0,.2)",
      }}
    >
      <IconButton
        id="close-right-panel-btn"
        color="secondary"
        size="small"
        aria-label="Close button"
        onClick={setLeftPanelExpanded}
        data-cy="workflow-definition-close-right-panel-button"
      >
        <DoubleArrowRightIcon />
      </IconButton>
      <Tabs
        value={openedTab}
        contextual={false}
        variant="scrollable"
        scrollButtons={false}
        allowScrollButtonsMobile
        style={{
          marginBottom: 0,
        }}
      >
        <Tab
          value={WORKFLOW_TAB}
          label={
            <WorkflowTabContent
              id="workflow-metadata-tab"
              ref={workflowTabRef}
            />
          }
          onClick={() => changeTab(WORKFLOW_TAB)}
          disabled={
            (
              definitionActor as ActorRefWithChildren<WorkflowDefinitionEvents>
            ).children?.get("flowMachine") == null
          }
        />
        <Tab
          value={TASK_TAB}
          id="task-tab"
          label="Task"
          onClick={() => changeTab(TASK_TAB)}
          disabled={
            (
              definitionActor as ActorRefWithChildren<WorkflowDefinitionEvents>
            ).children?.get("flowMachine") == null
          }
        />
        <Tab
          value={CODE_TAB}
          id="code-tab"
          data-cy="workflow-definition-code-tab"
          label="Code"
          onClick={() => changeTab(CODE_TAB)}
        />
        <Tab
          value={RUN_TAB}
          id="run-tab"
          label={<div ref={runTabRef}>Run</div>}
          onClick={() => changeTab(RUN_TAB)}
        />
        {isPlayground ? (
          <Tab
            value={DEPENDENCIES_TAB}
            id="dependencies-tab"
            label={
              <Box
                ref={dependenciesTabRef}
                sx={{
                  position: "relative",
                  display: "inline-block",
                  width: "100%",
                }}
              >
                <span style={{ display: "block", textAlign: "center" }}>
                  Dependencies
                </span>
                <Badge
                  color="primary"
                  overlap="circular"
                  sx={{
                    position: "absolute",
                    top: 0,
                    right: 0,
                    transform: "translate(50%,-50%)",
                    zIndex: 1,
                    "& .MuiBadge-badge": {
                      fontSize: "0.75rem",
                      minWidth: 16,
                      height: 16,
                      padding: "0 4px",
                    },
                  }}
                />
              </Box>
            }
            onClick={() => changeTab(DEPENDENCIES_TAB)}
          />
        ) : null}
      </Tabs>

      {workflowTabRef.current && (
        <CustomTooltip
          maxWidth={460}
          open={isFirstTimeFlowWorkflowDialog}
          anchorEl={workflowTabRef.current}
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
                <Box
                  component="span"
                  sx={{ fontSize: "14px", fontWeight: 600 }}
                >
                  Congratulations! Your first workflow is ready to use.
                </Box>
                <Box component="span" sx={{ fontSize: "12px" }}>
                  🎉
                </Box>
              </Box>
              <Box sx={{ color: "#252525" }}>
                You can define inputs and outputs for your workflow or add an
                optional JSON schema verification. Discover more features down
                the form!
              </Box>
              <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
                <Button
                  onClick={handleNextButtonClick}
                  size="small"
                  id="btn-metadata-tutorial-next"
                >
                  Next
                </Button>
              </Box>
            </Stack>
          }
          placement="bottom-start"
        />
      )}

      {runTabRef.current && (
        <CustomTooltip
          open={isShowRunMessageDialog}
          anchorEl={runTabRef.current}
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
                <Box
                  component="span"
                  sx={{ fontSize: "14px", fontWeight: 600 }}
                >
                  Before you execute
                </Box>
                <Box component="span" sx={{ fontSize: "12px" }}>
                  🚀
                </Box>
              </Box>
              <Box sx={{ color: "#252525" }}>
                You can test your workflow with different arguments by editing
                the Input params.
              </Box>
              <Box sx={{ color: "#252525" }}>
                <strong>Pro Tip:</strong> Idempotency key is a unique,
                user-generated key to prevent duplicate executions.
              </Box>
              <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
                <Button
                  onClick={handleNextButtonClick}
                  size="small"
                  id="btn-run-tutorial-next"
                >
                  Next
                </Button>
              </Box>
            </Stack>
          }
          placement="bottom-start"
        />
      )}
      {dependenciesTabRef.current && (
        <CustomTooltip
          open={isShowDependenciesDialog}
          anchorEl={dependenciesTabRef.current}
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
                <Box
                  component="span"
                  sx={{ fontSize: "14px", fontWeight: 600 }}
                >
                  Before you execute
                </Box>
                <Box component="span" sx={{ fontSize: "12px" }}>
                  🔗
                </Box>
              </Box>
              <Box sx={{ color: "#252525" }}>
                Your workflow depends on integrations and models. Before
                executing, make sure to configure them correctly.
              </Box>
              <Box sx={{ color: "#252525" }}>
                You can add dependencies to your workflow by going to the
                integration menu
              </Box>
              <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
                <Button
                  onClick={handleNextButtonClick}
                  size="small"
                  id="btn-run-tutorial-next"
                >
                  Next
                </Button>
              </Box>
            </Stack>
          }
          placement="bottom-start"
        />
      )}
    </Box>
  );
};

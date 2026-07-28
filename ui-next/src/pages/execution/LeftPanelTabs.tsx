import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import { Box, Button } from "@mui/material";
import { Tab, Tabs } from "components";
import { agentFirstUseAtom } from "components/features/agent/agentAtomsStore";
import { useAtom } from "jotai";
import { WorkflowExecution } from "types/Execution";
import { featureFlags, FEATURES } from "utils/flags";
import { isAgentWorkflowExecution } from "./helpers";
import { ExecutionTabs } from "./state/types";

export interface LeftPanelTabsProps {
  execution: WorkflowExecution;
  openedTab: ExecutionTabs;
  onChangeExecutionTab: (tab: ExecutionTabs) => void;
  onToggleAssistant?: () => void;
  isAssistantOpen?: boolean;
}

const isWorkflowIntrospectionEnabled = featureFlags.isEnabled(
  FEATURES.WORKFLOW_INTROSPECTION,
);

const showAgent = featureFlags.isEnabled(FEATURES.SHOW_AGENT);

export default function LeftPanelTabs({
  execution,
  openedTab,
  onChangeExecutionTab,
  onToggleAssistant,
  isAssistantOpen,
}: LeftPanelTabsProps) {
  const [firstUse] = useAtom(agentFirstUseAtom);

  const isAgentWorkflow = isAgentWorkflowExecution(execution);

  // Agent-classified executions get a curated tab set that matches
  // AgentSpan's own UI 1:1: Agent Execution, Workflow View (Diagram, relabeled),
  // Task List, Timeline, Agent Definition (Summary, relabeled), JSON — no
  // Workflow Input/Output, Variables, or Tasks to Domain. Regular workflows
  // keep the full Conductor tab set unchanged.
  const leftPanelTabItems = isAgentWorkflow
    ? [
        {
          label: "Agent Execution",
          onClick: () =>
            onChangeExecutionTab(ExecutionTabs.AGENT_EXECUTION_TAB),
          value: ExecutionTabs.AGENT_EXECUTION_TAB,
        },
        {
          label: "Workflow View",
          onClick: () => onChangeExecutionTab(ExecutionTabs.DIAGRAM_TAB),
          value: ExecutionTabs.DIAGRAM_TAB,
        },
        {
          label: "Task List",
          onClick: () => onChangeExecutionTab(ExecutionTabs.TASK_LIST_TAB),
          value: ExecutionTabs.TASK_LIST_TAB,
        },
        {
          label: "Timeline",
          onClick: () => onChangeExecutionTab(ExecutionTabs.TIMELINE_TAB),
          value: ExecutionTabs.TIMELINE_TAB,
        },
        {
          label: "Agent Definition",
          onClick: () => onChangeExecutionTab(ExecutionTabs.SUMMARY_TAB),
          value: ExecutionTabs.SUMMARY_TAB,
        },
        {
          label: "JSON",
          onClick: () => onChangeExecutionTab(ExecutionTabs.JSON_TAB),
          value: ExecutionTabs.JSON_TAB,
        },
      ]
    : [
        {
          label: "Diagram",
          onClick: () => onChangeExecutionTab(ExecutionTabs.DIAGRAM_TAB),
          value: ExecutionTabs.DIAGRAM_TAB,
        },
        {
          label: "Task List",
          onClick: () => onChangeExecutionTab(ExecutionTabs.TASK_LIST_TAB),
          value: ExecutionTabs.TASK_LIST_TAB,
        },
        {
          label: "Timeline",
          onClick: () => onChangeExecutionTab(ExecutionTabs.TIMELINE_TAB),
          value: ExecutionTabs.TIMELINE_TAB,
        },
        {
          label: "Summary",
          onClick: () => onChangeExecutionTab(ExecutionTabs.SUMMARY_TAB),
          value: ExecutionTabs.SUMMARY_TAB,
        },
        {
          label: "Workflow Input/Output",
          onClick: () =>
            onChangeExecutionTab(ExecutionTabs.WORKFLOW_INPUT_OUTPUT_TAB),
          value: ExecutionTabs.WORKFLOW_INPUT_OUTPUT_TAB,
        },
        {
          label: "JSON",
          onClick: () => onChangeExecutionTab(ExecutionTabs.JSON_TAB),
          value: ExecutionTabs.JSON_TAB,
        },
        {
          label: "Variables",
          onClick: () => onChangeExecutionTab(ExecutionTabs.VARIABLES_TAB),
          value: ExecutionTabs.VARIABLES_TAB,
        },
        {
          label: "Tasks to Domain",
          onClick: () =>
            onChangeExecutionTab(ExecutionTabs.TASKS_TO_DOMAIN_TAB),
          value: ExecutionTabs.TASKS_TO_DOMAIN_TAB,
        },
      ];

  // Add Workflow Introspection tab only if the feature flag is enabled —
  // inserted right after "Timeline" in either tab set (matches AgentSpan's
  // own splice position for the agent set, and the pre-existing position
  // for the regular set).
  if (isWorkflowIntrospectionEnabled) {
    const timelineIndex = leftPanelTabItems.findIndex(
      (item) => item.value === ExecutionTabs.TIMELINE_TAB,
    );
    leftPanelTabItems.splice(timelineIndex + 1, 0, {
      label: "Workflow Introspection",
      onClick: () => onChangeExecutionTab(ExecutionTabs.WORKFLOW_INTROSPECTION),
      value: ExecutionTabs.WORKFLOW_INTROSPECTION,
    });
  }

  return (
    <Box sx={{ display: "flex", alignItems: "center" }}>
      <Tabs
        value={openedTab}
        style={{ marginBottom: 0, flexGrow: 1, minWidth: 0 }}
        contextual
        variant="scrollable"
        scrollButtons={"auto"}
        allowScrollButtonsMobile
      >
        {leftPanelTabItems.map(({ label, onClick, value }) => (
          <Tab key={label} label={label} onClick={onClick} value={value} />
        ))}
      </Tabs>

      {showAgent && onToggleAssistant && (
        <Box sx={{ flexShrink: 0, px: 1 }}>
          <Button
            size="small"
            variant="text"
            onClick={onToggleAssistant}
            startIcon={
              <AutoAwesomeIcon
                sx={{
                  fontSize: "14px !important",
                  animation:
                    !firstUse && !isAssistantOpen
                      ? "rotate-color 3s ease-in-out infinite"
                      : "none",
                  "@keyframes rotate-color": {
                    "0%, 100%": { color: "rgba(36, 157, 233, 0.74)" },
                    "50%": { color: "rgba(212, 13, 219, 0.74)" },
                  },
                }}
              />
            }
            sx={{
              textTransform: "none",
              fontSize: "0.8rem",
              py: 0.5,
              color: isAssistantOpen ? "primary.main" : "text.secondary",
              fontWeight: isAssistantOpen ? 600 : 400,
              "&:hover": {
                backgroundColor: "rgba(0,0,0,0.04)",
                color: "primary.main",
              },
            }}
          >
            Assistant
          </Button>
        </Box>
      )}
    </Box>
  );
}

import { Tab, Tabs } from "components";
import { ExecutionTabs } from "./state/types";
import { WorkflowExecution } from "types/Execution";
import { featureFlags, FEATURES } from "utils/flags";
import { agentFirstUseAtom } from "components/features/agent/agentAtomsStore";
import { useAtom } from "jotai";

export interface LeftPanelTabsProps {
  execution: WorkflowExecution;
  openedTab: boolean;
  onChangeExecutionTab: (tab: ExecutionTabs) => void;
  onToggleAssistant: () => void;
}

const isWorkflowIntrospectionEnabled = featureFlags.isEnabled(
  FEATURES.WORKFLOW_INTROSPECTION,
);

const showAgent = featureFlags.isEnabled(FEATURES.SHOW_AGENT);

export default function LeftPanelTabs({
  openedTab,
  onChangeExecutionTab,
  onToggleAssistant,
}: LeftPanelTabsProps) {
  const [firstUse] = useAtom(agentFirstUseAtom);

  const leftPanelTabItems = [
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
      onClick: () => onChangeExecutionTab(ExecutionTabs.TASKS_TO_DOMAIN_TAB),
      value: ExecutionTabs.TASKS_TO_DOMAIN_TAB,
    },
    ...(showAgent
      ? [
          {
            label: "Assistant",
            onClick: onToggleAssistant,
            value: null,
            tabSx: {
              animation: !firstUse
                ? "rotate-color 3s ease-in-out infinite"
                : "none",
              "@keyframes rotate-color": {
                "0%, 100%": {
                  color: "rgba(36, 157, 233, 0.74)",
                },
                "50%": {
                  color: "rgba(212, 13, 219, 0.74)",
                },
              },
            },
          },
        ]
      : []),
  ];

  // Add Workflow Introspection tab only if the feature flag is enabled
  if (isWorkflowIntrospectionEnabled) {
    leftPanelTabItems.splice(3 /* After the timeline tab */, 0, {
      label: "Workflow Introspection",
      onClick: () => onChangeExecutionTab(ExecutionTabs.WORKFLOW_INTROSPECTION),
      value: ExecutionTabs.WORKFLOW_INTROSPECTION,
    });
  }

  return (
    <Tabs
      value={openedTab}
      style={{ marginBottom: 0 }}
      contextual
      variant="scrollable"
      scrollButtons={"auto"}
      allowScrollButtonsMobile
    >
      {leftPanelTabItems.map(({ label, onClick, value, tabSx }) => (
        <Tab
          key={label}
          label={label}
          onClick={onClick}
          value={value}
          sx={tabSx}
        />
      ))}
    </Tabs>
  );
}

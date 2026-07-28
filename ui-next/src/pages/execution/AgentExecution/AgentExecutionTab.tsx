import { useState, useMemo, useCallback } from "react";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { AGENT_EXECUTIONS_URL } from "utils/constants/route";
import { Box, MenuItem, Select, SelectChangeEvent, Alert } from "@mui/material";
import { AgentExecutionHeader } from "./AgentExecutionHeader";
import { AgentRunView } from "./AgentRunView";
import {
  computeMetrics,
  transformWorkflowExecutionToAgentRun,
} from "./agentExecutionUtils";
import {
  DEFAULT_MOCK_SCENARIO,
  MOCK_SCENARIOS,
  MockScenarioKey,
} from "./mockData";
import { AgentRunData, AgentStatus } from "./types";
import { WorkflowExecution, WorkflowExecutionStatus } from "types/Execution";
import { HumanInputPanel } from "./HumanInputPanel";

/** Sub-agents and parent workflows reached from the debugger are themselves
 * agent executions — route them through /agentExecutions/:id so the sidebar
 * keeps "Executions" (under Agents) highlighted, not the plain Workflow item. */
function agentExecutionPath(id: string): string {
  return `${AGENT_EXECUTIONS_URL.BASE}/${id}`;
}

interface AgentExecutionTabProps {
  execution?: WorkflowExecution;
}

/**
 * Detect wrapper workflows — thin shells created by the SDK around an actual agent
 * (e.g. engineering_lead_swarm_wf wraps engineering_lead_inner).
 * Heuristic: ≤5 tasks, one of which is a SUB_WORKFLOW named *_inner.
 */
function findInnerSubWorkflowId(execution: WorkflowExecution): string | null {
  const tasks = execution.tasks ?? [];
  if (tasks.length > 6) return null;
  const innerTask = tasks.find(
    (t) =>
      t.taskType === "SUB_WORKFLOW" && t.referenceTaskName.includes("_inner"),
  );
  return (innerTask?.outputData?.subWorkflowId as string | undefined) ?? null;
}

export function AgentExecutionTab({ execution }: AgentExecutionTabProps) {
  // Only show scenario selector when no real execution is provided
  const [scenario, setScenario] = useState<MockScenarioKey>(
    DEFAULT_MOCK_SCENARIO,
  );

  const { rootRun, transformError } = useMemo(() => {
    if (execution?.workflowId) {
      try {
        return {
          rootRun: transformWorkflowExecutionToAgentRun(execution),
          transformError: null,
        };
      } catch (err) {
        const msg =
          err instanceof Error
            ? `${err.message}\n\n${err.stack ?? ""}`
            : String(err);
        console.error("[AgentExecution] Transform failed:", err);
        return {
          rootRun: {
            id: execution.workflowId,
            agentName:
              execution.workflowName ?? execution.workflowType ?? "agent",
            turns: [],
            status: AgentStatus.FAILED,
            totalTokens: {
              promptTokens: 0,
              completionTokens: 0,
              totalTokens: 0,
            },
            totalDurationMs: 0,
          } as AgentRunData,
          transformError: msg,
        };
      }
    }
    return { rootRun: MOCK_SCENARIOS[scenario], transformError: null };
  }, [execution?.workflowId, execution?.tasks, scenario]);

  const navigate = usePushHistory();

  // Drill into a sub-agent by navigating to its execution URL.
  // For wrapper workflows, resolve the inner sub-workflow ID first.
  const onDrillIn = useCallback(
    async (sub: AgentRunData) => {
      const targetId = sub.subWorkflowId ?? sub.id;
      if (!targetId) return;

      // Check if this is a wrapper workflow and resolve inner ID
      try {
        const res = await fetch(`/api/agent/executions/${targetId}/full`);
        if (res.ok) {
          const subExecution: WorkflowExecution = await res.json();
          const innerId = findInnerSubWorkflowId(subExecution);
          if (innerId) {
            navigate(agentExecutionPath(innerId));
            return;
          }
        }
      } catch {
        // Fall through to direct navigation
      }

      navigate(agentExecutionPath(targetId));
    },
    [navigate],
  );

  // Back navigates to the parent workflow
  const onBack = execution?.parentWorkflowId
    ? () => navigate(agentExecutionPath(execution.parentWorkflowId!))
    : undefined;

  const metrics = computeMetrics(rootRun);
  const isUsingMockData = !execution?.workflowId;
  const isPaused = execution?.status === WorkflowExecutionStatus.PAUSED;

  // Collect sub-agent names for MANUAL strategy selection
  const subAgentNames = useMemo<string[]>(() => {
    const agentDef = rootRun.agentDef as Record<string, unknown> | undefined;
    const agents = agentDef?.agents as Array<{ name?: string }> | undefined;
    if (Array.isArray(agents)) {
      return agents.map((a) => a.name ?? "").filter(Boolean);
    }
    return rootRun.turns.flatMap((t) => t.subAgents.map((s) => s.agentName));
  }, [rootRun]);

  const handleScenarioChange = (event: SelectChangeEvent<MockScenarioKey>) => {
    setScenario(event.target.value as MockScenarioKey);
  };

  return (
    <Box
      sx={{
        height: "100%",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      {transformError && (
        <Alert
          severity="error"
          sx={{ mx: 2, mt: 1, flexShrink: 0, fontSize: "0.75rem" }}
        >
          <strong>Failed to parse execution data.</strong> Check the browser
          console for details.
          <Box
            component="pre"
            sx={{
              mt: 0.5,
              fontSize: "0.7rem",
              whiteSpace: "pre-wrap",
              wordBreak: "break-word",
              maxHeight: 120,
              overflowY: "auto",
            }}
          >
            {transformError}
          </Box>
        </Alert>
      )}

      {/* Human-in-the-loop input panel (only when execution is paused) */}
      {isPaused && execution?.workflowId && (
        <HumanInputPanel
          executionId={execution.workflowId}
          subAgentNames={subAgentNames}
        />
      )}

      {/* Header row: metrics + scenario selector (mock only) */}
      <Box sx={{ display: "flex", alignItems: "center", flexShrink: 0 }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <AgentExecutionHeader metrics={metrics} rootRun={rootRun} />
        </Box>
        {isUsingMockData && (
          <Box sx={{ px: 1.5, py: 1, flexShrink: 0 }}>
            <Select<MockScenarioKey>
              value={scenario}
              onChange={handleScenarioChange}
              size="small"
              variant="outlined"
              sx={{
                fontSize: "0.75rem",
                "& .MuiSelect-select": { py: 0.5, px: 1 },
              }}
            >
              {(Object.keys(MOCK_SCENARIOS) as MockScenarioKey[]).map((key) => (
                <MenuItem key={key} value={key} sx={{ fontSize: "0.75rem" }}>
                  {key}
                </MenuItem>
              ))}
            </Select>
          </Box>
        )}
      </Box>

      {/* Main content */}
      <Box
        sx={{ flex: 1, overflow: "hidden", minHeight: 0, position: "relative" }}
      >
        <AgentRunView
          agentRun={rootRun}
          onDrillIn={onDrillIn}
          onBack={onBack}
          isRoot
        />
      </Box>
    </Box>
  );
}

export default AgentExecutionTab;

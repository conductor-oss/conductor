import { WorkflowSearch } from "pages/executions";
import { useSearchParams } from "react-router-dom";

/**
 * Agent executions reuse the workflow executions search UI, scoped to the
 * "agent" classifier. AgentSpan agent runs are native Conductor workflows, so
 * they are searchable through the same /workflow/search endpoint.
 */
export default function AgentExecutions() {
  const [searchParams] = useSearchParams();
  const agentName = searchParams.get("agentName") || "";

  return (
    <WorkflowSearch
      classifier="agent"
      title="Agent Executions"
      agentName={agentName}
      headerActions={null}
      excludeSubLabel="Exclude sub-agents"
    />
  );
}

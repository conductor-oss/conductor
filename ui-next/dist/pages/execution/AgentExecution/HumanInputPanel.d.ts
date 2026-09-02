/**
 * HumanInputPanel — shown when an agent execution is PAUSED awaiting human input.
 *
 * Handles two cases:
 *   1. Tool approval  (@tool(approval_required=True)) — approve/reject buttons
 *   2. MANUAL strategy agent selection — dropdown + confirm button
 *
 * Talks to the embedded Conductor-Agents REST API (conductor-agentspan module,
 * gated by conductor.integrations.ai.enabled) — /api/agent/:id/status and
 * /api/agent/:id/respond.
 */
interface HumanInputPanelProps {
    executionId: string;
    /** List of sub-agent names for MANUAL strategy selection */
    subAgentNames?: string[];
}
export declare function HumanInputPanel({ executionId, subAgentNames, }: HumanInputPanelProps): import("react").JSX.Element | null;
export default HumanInputPanel;

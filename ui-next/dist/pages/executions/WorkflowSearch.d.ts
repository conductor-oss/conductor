import { ReactNode } from "react";
export interface WorkflowSearchProps {
    /** Classifier filter passed to /workflow/search ("workflow" | "agent"). */
    classifier?: string;
    /** When set, scopes results to a single agent and shows it in the title. */
    agentName?: string;
    /** Page and document title. */
    title?: string;
    /** Header actions; pass `null` to render none. Defaults to workflow actions. */
    headerActions?: ReactNode;
    /**
     * When set, the basic search renders a toggle with this label that excludes
     * sub-executions (those with a parentWorkflowId) — e.g. "Exclude sub-agents".
     */
    excludeSubLabel?: string;
}
export default function WorkflowPanel({ classifier, agentName, title, headerActions, excludeSubLabel, }?: WorkflowSearchProps): import("react").JSX.Element;

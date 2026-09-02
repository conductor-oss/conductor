/**
 * Hook that automatically detects the current page context and updates the AI context atom.
 * This determines which AI prompt and tools are available based on the active route.
 *
 * Usage: Call this hook in a global layout component (like SideAndTopBarsLayout)
 *
 * Context Mapping:
 * - /workflow/[id]/edit -> "workflow_builder"
 * - /workflows, /workflowDef -> "workflow_search"
 * - /execution/[id] -> "execution_details"
 * - /taskDef -> "task_definitions"
 * - /integrations -> "integrations"
 * - Everything else -> "general"
 */
export declare const useAiContext: () => void;

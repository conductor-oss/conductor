import { useEffect } from "react";
import { useLocation } from "react-router";
import { useSetAtom } from "jotai";
import { aiContextAtom, aiContextDataAtom } from "./agentAtomsStore";

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
export const useAiContext = () => {
  const location = useLocation();
  const setAiContext = useSetAtom(aiContextAtom);
  const setAiContextData = useSetAtom(aiContextDataAtom);

  useEffect(() => {
    const path = location.pathname;
    let newContext = "general";
    const contextData: Record<string, any> = {};

    // Workflow builder (editing a workflow OR creating new)
    // /workflowDef/<name> is the builder screen
    // /newWorkflowDef is creating a new workflow
    if (
      path.includes("/workflowDef/") ||
      path.includes("/newWorkflowDef") ||
      (path.includes("/workflow/") && path.includes("/edit"))
    ) {
      newContext = "workflow_builder";
    }
    // Workflow search/list - /workflows (plural, no specific workflow)
    else if (path === "/workflows" || path.startsWith("/workflows?")) {
      newContext = "workflow_search";
    }
    // Execution search/list - /executions (plural)
    else if (path === "/executions" || path.startsWith("/executions?")) {
      newContext = "execution_search";
    }
    // Execution details - /execution/<uuid> (singular)
    else if (path.includes("/execution/") && path.split("/").length >= 3) {
      newContext = "execution_details";
      // Extract execution ID from URL
      const parts = path.split("/");
      const executionIndex = parts.indexOf("execution");
      if (executionIndex >= 0 && parts[executionIndex + 1]) {
        contextData.executionId = parts[executionIndex + 1];
        console.log(`📋 Execution ID: ${contextData.executionId}`);
      }
    }
    // Task definitions
    else if (path.includes("/taskDef")) {
      newContext = "task_definitions";
    }
    // Integrations
    else if (path.includes("/integrations")) {
      newContext = "integrations";
    }
    // Default to general context
    else {
      newContext = "general";
    }

    setAiContext(newContext);
    setAiContextData(contextData);
  }, [location.pathname, setAiContext, setAiContextData]);
};

import { WorkflowDefinitionEvents } from "pages/definition/state";
import { ActorRef } from "xstate";
import { AgentContentTab, AgentDisplayMode, Conversation } from "components/features/agent/agent-types";
import { WorkflowDef } from "types/WorkflowDef";
import { CreateAndDisplayApplicationEvents } from "shared/createAndDisplayApplication/state/types";
export declare const setDefinitionServiceAtom: import("jotai").WritableAtom<null, [service: ActorRef<WorkflowDefinitionEvents, any>], void> & {
    init: null;
};
export declare const definitionActorAtom: import("jotai").PrimitiveAtom<ActorRef<WorkflowDefinitionEvents, any> | null> & {
    init: ActorRef<WorkflowDefinitionEvents, any> | null;
};
export declare const createAndDisplayApplicationActorAtom: import("jotai").PrimitiveAtom<ActorRef<CreateAndDisplayApplicationEvents, any> | null> & {
    init: ActorRef<CreateAndDisplayApplicationEvents, any> | null;
};
export declare const agentDisplayModeAtom: import("jotai").WritableAtom<AgentDisplayMode, [AgentDisplayMode | typeof import("jotai/utils").RESET | ((prev: AgentDisplayMode) => AgentDisplayMode | typeof import("jotai/utils").RESET)], void>;
/**
 * Filled while the workflow execution page is mounted. Lets global Assistant
 * controls (e.g. sidebar) close the task detail panel so the assistant can show,
 * matching the in-page Assistant tab next to execution tabs.
 */
export declare const executionAssistantBridge: {
    closeRightPanel: (() => void) | null;
    isTaskPanelOpen: boolean;
};
export declare const agentWidthAtom: import("jotai").WritableAtom<number, [number | typeof import("jotai/utils").RESET | ((prev: number) => number | typeof import("jotai/utils").RESET)], void>;
export declare const messagesAtom: import("jotai").PrimitiveAtom<[]> & {
    init: [];
};
export declare const sessionIdAtom: import("jotai").PrimitiveAtom<string | null> & {
    init: string | null;
};
export declare const isConnectedAtom: import("jotai").PrimitiveAtom<boolean> & {
    init: boolean;
};
export declare const isStreamingAtom: import("jotai").PrimitiveAtom<boolean> & {
    init: boolean;
};
export declare const workflowNameAtom: import("jotai").PrimitiveAtom<string | null> & {
    init: string | null;
};
export declare const currentWorkflowAtom: import("jotai").PrimitiveAtom<Partial<WorkflowDef> | null> & {
    init: Partial<WorkflowDef> | null;
};
export declare const errorAtom: import("jotai").PrimitiveAtom<string | null> & {
    init: string | null;
};
export declare const tokenUsageAtom: import("jotai").PrimitiveAtom<any> & {
    init: any;
};
/**
 * Current AI context based on the active page/route.
 * Determines which prompt and tools are available to the AI.
 *
 * Possible values:
 * - "general" - Q&A and help (default)
 * - "workflow_builder" - Workflow building page
 * - "workflow_search" - Workflow search/list page
 * - "execution_search" - Execution search/list page
 * - "execution_details" - Execution details page
 * - "task_definitions" - Task definitions page
 * - "integrations" - Integrations page
 */
export declare const aiContextAtom: import("jotai").PrimitiveAtom<string> & {
    init: string;
};
/**
 * Additional context-specific data to send with AI requests.
 * For example: execution ID when on execution details page.
 */
export declare const aiContextDataAtom: import("jotai").PrimitiveAtom<Record<string, any>> & {
    init: Record<string, any>;
};
/**
 * The current tab of the agent content.
 * Possible values:
 * - AgentContentTab.CHAT - Chat tab (default)
 * - AgentContentTab.CONVERSATIONS - Conversations tab
 */
export declare const agentContentTabAtom: import("jotai").PrimitiveAtom<AgentContentTab> & {
    init: AgentContentTab;
};
/**
 * The conversations list.
 * Populated dynamically from the backend API.
 */
export declare const conversationsAtom: import("jotai").PrimitiveAtom<Conversation[]> & {
    init: Conversation[];
};
/**
 * Whether the agent has been used for the first time.
 * Used to show the button highlight.
 */
export declare const agentFirstUseAtom: import("jotai").WritableAtom<boolean, [boolean | typeof import("jotai/utils").RESET | ((prev: boolean) => boolean | typeof import("jotai/utils").RESET)], void>;
export interface CodeAttachment {
    id: string;
    filename: string;
    messageId: string;
}
export declare const codeAttachmentsAtom: import("jotai").PrimitiveAtom<CodeAttachment[]> & {
    init: CodeAttachment[];
};
export declare const addCodeAttachmentAtom: import("jotai").WritableAtom<null, [attachment: CodeAttachment], void> & {
    init: null;
};
export declare const removeCodeAttachmentAtom: import("jotai").WritableAtom<null, [attachmentId: string], void> & {
    init: null;
};
export declare const clearCodeAttachmentsAtom: import("jotai").WritableAtom<null, [], void> & {
    init: null;
};
/**
 * Integration configuration request from AI chat.
 * When set, shows the integration dialog and disables the chat.
 */
export interface IntegrationConfigurationRequest {
    integrationType: string;
    suggestedName: string;
    reason?: string;
    prefilledValues?: Record<string, string | number | boolean>;
    resumeContext?: string;
}
export declare const integrationConfigurationRequestAtom: import("jotai").PrimitiveAtom<IntegrationConfigurationRequest | null> & {
    init: IntegrationConfigurationRequest | null;
};

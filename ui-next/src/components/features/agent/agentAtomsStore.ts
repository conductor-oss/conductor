// Temporary store for agent state,
// will be replaced with a more permanent store after migrating to the latest XState.

import { atom } from "jotai";
import { WorkflowDefinitionEvents } from "pages/definition/state";
import { ActorRef } from "xstate";
import {
  AgentContentTab,
  AgentDisplayMode,
  Conversation,
} from "components/features/agent/agent-types";
import { WorkflowDef } from "types/WorkflowDef";
import { atomWithStorage } from "jotai/utils";
import { CreateAndDisplayApplicationEvents } from "shared/createAndDisplayApplication/state/types";

export const setDefinitionServiceAtom = atom(
  null,
  (get, set, service: ActorRef<WorkflowDefinitionEvents>) => {
    set(definitionActorAtom, service);
  },
);

export const definitionActorAtom =
  atom<ActorRef<WorkflowDefinitionEvents> | null>(null);

export const createAndDisplayApplicationActorAtom =
  atom<ActorRef<CreateAndDisplayApplicationEvents> | null>(null);

export const agentDisplayModeAtom = atomWithStorage<AgentDisplayMode>(
  "agentDisplayMode",
  AgentDisplayMode.FLOATING_MINIMIZED,
);

export const agentWidthAtom = atomWithStorage<number>("agentWidth", 400);

export const messagesAtom = atom<[]>([]);

export const sessionIdAtom = atom<string | null>(null);

export const isConnectedAtom = atom<boolean>(true);

export const isStreamingAtom = atom<boolean>(false);

export const workflowNameAtom = atom<string | null>(null);

export const currentWorkflowAtom = atom<Partial<WorkflowDef> | null>(null);

export const errorAtom = atom<string | null>(null);

export const tokenUsageAtom = atom<any>(null);

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
export const aiContextAtom = atom<string>("general");

/**
 * Additional context-specific data to send with AI requests.
 * For example: execution ID when on execution details page.
 */
export const aiContextDataAtom = atom<Record<string, any>>({});

/**
 * The current tab of the agent content.
 * Possible values:
 * - AgentContentTab.CHAT - Chat tab (default)
 * - AgentContentTab.CONVERSATIONS - Conversations tab
 */
export const agentContentTabAtom = atom<AgentContentTab>(AgentContentTab.CHAT);

/**
 * The conversations list.
 * Populated dynamically from the backend API.
 */
export const conversationsAtom = atom<Conversation[]>([]);

/**
 * Whether the agent has been used for the first time.
 * Used to show the button highlight.
 */
export const agentFirstUseAtom = atomWithStorage<boolean>(
  "agentFirstUse",
  false,
);

export interface CodeAttachment {
  id: string;
  filename: string;
  messageId: string;
}

export const codeAttachmentsAtom = atom<CodeAttachment[]>([]);

export const addCodeAttachmentAtom = atom(
  null,
  (get, set, attachment: CodeAttachment) => {
    const currentAttachments = get(codeAttachmentsAtom);
    set(codeAttachmentsAtom, [...currentAttachments, attachment]);
  },
);

export const removeCodeAttachmentAtom = atom(
  null,
  (get, set, attachmentId: string) => {
    const currentAttachments = get(codeAttachmentsAtom);
    set(
      codeAttachmentsAtom,
      currentAttachments.filter((a) => a.id !== attachmentId),
    );
  },
);

export const clearCodeAttachmentsAtom = atom(null, (get, set) => {
  set(codeAttachmentsAtom, []);
});

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

export const integrationConfigurationRequestAtom =
  atom<IntegrationConfigurationRequest | null>(null);

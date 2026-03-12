export enum AgentDisplayMode {
  FLOATING_EXPANDED = "floating-expanded",
  FLOATING_MINIMIZED = "floating-minimized",
  TABBED = "tabbed",
  CLOSED = "closed",
  FULL_PAGE = "full-page",
  RIGHT_SIDEBAR = "right-sidebar",
}

export enum AgentContentTab {
  CHAT = "chat",
  CONVERSATIONS = "conversations",
}
export interface Message {
  role: "user" | "assistant";
  content: string;
}
export interface Conversation {
  sessionId: string;
  title: string;
  messageCount: number;
  workflowName?: string;
  createdAt: string;
  updatedAt: string;
  status: string;
}

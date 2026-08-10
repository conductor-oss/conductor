import { createContext, useContext, ReactNode, useMemo } from "react";

type AgentContextType = {
  sendMessage: (message: string) => void;
  applySuggestion: (
    messageId: string,
    accepted: boolean,
    feedback?: string,
  ) => void;
  clearMessages: () => void;
  cancelStream: () => void;
  resumeStream?: () => void;
};

const AgentContext = createContext<AgentContextType | null>(null);

export function AgentProvider({
  children,
  sendMessage,
  applySuggestion,
  clearMessages,
  cancelStream,
  resumeStream,
}: {
  children: ReactNode;
  sendMessage: (message: string) => void;
  applySuggestion: (
    messageId: string,
    accepted: boolean,
    feedback?: string,
  ) => void;
  clearMessages: () => void;
  cancelStream: () => void;
  resumeStream?: () => void;
}) {
  const value = useMemo(
    () => ({
      sendMessage,
      applySuggestion,
      clearMessages,
      cancelStream,
      resumeStream,
    }),
    [sendMessage, applySuggestion, clearMessages, cancelStream, resumeStream],
  );

  return (
    <AgentContext.Provider value={value}>{children}</AgentContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAgentContext() {
  const context = useContext(AgentContext);
  if (!context) {
    // Return no-op functions if not in provider context
    return {
      sendMessage: () => {},
      applySuggestion: () => {},
      clearMessages: () => {},
      cancelStream: () => {},
      resumeStream: undefined,
    };
  }
  return context;
}

import { ReactNode } from "react";
type AgentContextType = {
    sendMessage: (message: string) => void;
    applySuggestion: (messageId: string, accepted: boolean, feedback?: string) => void;
    clearMessages: () => void;
    cancelStream: () => void;
    resumeStream?: () => void;
};
export declare function AgentProvider({ children, sendMessage, applySuggestion, clearMessages, cancelStream, resumeStream, }: {
    children: ReactNode;
    sendMessage: (message: string) => void;
    applySuggestion: (messageId: string, accepted: boolean, feedback?: string) => void;
    clearMessages: () => void;
    cancelStream: () => void;
    resumeStream?: () => void;
}): import("react").JSX.Element;
export declare function useAgentContext(): AgentContextType;
export {};

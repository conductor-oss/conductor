export type AgentGuideLanguage = "python" | "typescript" | "java" | "dotnet";
export type AgentGuide = {
    id: string;
    label: string;
    markdown: string;
};
export type AgentGuideLanguageDefinition = {
    id: AgentGuideLanguage;
    label: string;
    guides: AgentGuide[];
};
export declare const DEFAULT_AGENT_GUIDE_LANGUAGE: AgentGuideLanguage;
export declare const DEFAULT_AGENT_GUIDE_FRAMEWORK = "native";
export declare const AGENT_GUIDE_LANGUAGES: AgentGuideLanguageDefinition[];
export declare function getAgentGuideLanguage(language?: string | null): AgentGuideLanguageDefinition;
export declare function getAgentGuide(language: AgentGuideLanguageDefinition, framework?: string | null): AgentGuide;

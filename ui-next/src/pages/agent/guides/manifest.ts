import dotnetGoogleAdk from "./dotnet/google-adk.md?raw";
import dotnetNative from "./dotnet/native.md?raw";
import dotnetOpenAi from "./dotnet/openai.md?raw";
import dotnetSemanticKernel from "./dotnet/semantic-kernel.md?raw";
import javaGoogleAdk from "./java/google-adk.md?raw";
import javaLangChain4j from "./java/langchain4j.md?raw";
import javaLangGraph4j from "./java/langgraph4j.md?raw";
import javaNative from "./java/native.md?raw";
import javaOpenAi from "./java/openai.md?raw";
import pythonClaude from "./python/claude.md?raw";
import pythonGoogleAdk from "./python/google-adk.md?raw";
import pythonLangChain from "./python/langchain.md?raw";
import pythonLangGraph from "./python/langgraph.md?raw";
import pythonNative from "./python/native.md?raw";
import pythonOpenAi from "./python/openai.md?raw";
import typescriptGoogleAdk from "./typescript/google-adk.md?raw";
import typescriptLangChain from "./typescript/langchain.md?raw";
import typescriptLangGraph from "./typescript/langgraph.md?raw";
import typescriptNative from "./typescript/native.md?raw";
import typescriptOpenAi from "./typescript/openai.md?raw";
import typescriptVercelAi from "./typescript/vercel-ai.md?raw";

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

export const DEFAULT_AGENT_GUIDE_LANGUAGE: AgentGuideLanguage = "python";
export const DEFAULT_AGENT_GUIDE_FRAMEWORK = "native";

export const AGENT_GUIDE_LANGUAGES: AgentGuideLanguageDefinition[] = [
  {
    id: "python",
    label: "Python",
    guides: [
      { id: "native", label: "Conductor", markdown: pythonNative },
      { id: "openai", label: "OpenAI Agents", markdown: pythonOpenAi },
      { id: "langchain", label: "LangChain", markdown: pythonLangChain },
      { id: "langgraph", label: "LangGraph", markdown: pythonLangGraph },
      { id: "google-adk", label: "Google ADK", markdown: pythonGoogleAdk },
      { id: "claude", label: "Claude Agent SDK", markdown: pythonClaude },
    ],
  },
  {
    id: "typescript",
    label: "TypeScript",
    guides: [
      { id: "native", label: "Conductor", markdown: typescriptNative },
      { id: "openai", label: "OpenAI Agents", markdown: typescriptOpenAi },
      { id: "langchain", label: "LangChain", markdown: typescriptLangChain },
      { id: "langgraph", label: "LangGraph", markdown: typescriptLangGraph },
      { id: "google-adk", label: "Google ADK", markdown: typescriptGoogleAdk },
      { id: "vercel-ai", label: "Vercel AI SDK", markdown: typescriptVercelAi },
    ],
  },
  {
    id: "java",
    label: "Java",
    guides: [
      { id: "native", label: "Conductor", markdown: javaNative },
      { id: "openai", label: "OpenAI style", markdown: javaOpenAi },
      { id: "google-adk", label: "Google ADK", markdown: javaGoogleAdk },
      { id: "langchain4j", label: "LangChain4j", markdown: javaLangChain4j },
      { id: "langgraph4j", label: "LangGraph4j", markdown: javaLangGraph4j },
    ],
  },
  {
    id: "dotnet",
    label: ".NET",
    guides: [
      { id: "native", label: "Conductor", markdown: dotnetNative },
      { id: "openai", label: "OpenAI style", markdown: dotnetOpenAi },
      { id: "google-adk", label: "Google ADK", markdown: dotnetGoogleAdk },
      {
        id: "semantic-kernel",
        label: "Semantic Kernel",
        markdown: dotnetSemanticKernel,
      },
    ],
  },
];

export function getAgentGuideLanguage(language?: string | null) {
  return (
    AGENT_GUIDE_LANGUAGES.find((candidate) => candidate.id === language) ??
    AGENT_GUIDE_LANGUAGES[0]
  );
}

export function getAgentGuide(
  language: AgentGuideLanguageDefinition,
  framework?: string | null,
) {
  return (
    language.guides.find((candidate) => candidate.id === framework) ??
    language.guides[0]
  );
}

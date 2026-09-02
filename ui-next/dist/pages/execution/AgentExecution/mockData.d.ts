import { AgentRunData } from "./types";
export declare const MOCK_SCENARIOS: {
    readonly singleAgent: AgentRunData;
    readonly handoff: AgentRunData;
    readonly parallel: AgentRunData;
    readonly guardrailFailure: AgentRunData;
};
export type MockScenarioKey = keyof typeof MOCK_SCENARIOS;
export declare const DEFAULT_MOCK_SCENARIO: MockScenarioKey;

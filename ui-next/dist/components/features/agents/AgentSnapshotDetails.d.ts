import { AgentMetadataSnapshot } from "types";
export declare function AgentDefinitionDetails({ agentDef, title, }: {
    agentDef: Record<string, unknown>;
    title?: string;
}): import("react").JSX.Element;
export declare function AgentSnapshotDetails({ snapshot, }: {
    snapshot?: AgentMetadataSnapshot;
}): import("react").JSX.Element | null;

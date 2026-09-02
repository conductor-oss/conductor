import { DefinitionMachineContext } from "pages/definition/state/types";
import { fetchCloudTemplatesPreferCached, ImportSummary } from "utils/cloudTemplates";
export { fetchCloudTemplatesPreferCached };
export declare const fetchForImportedTemplateImportSummary: (context: DefinitionMachineContext) => Promise<ImportSummary | null>;
export declare const persistCopyInLocalStorage: (context: DefinitionMachineContext) => Promise<string>;
export declare const fetchSecrets: ({ authHeaders: headers, }: DefinitionMachineContext) => Promise<any>;
export declare const fetchInputSchema: ({ authHeaders: headers, currentWf, }: DefinitionMachineContext) => Promise<{
    schema?: undefined;
} | {
    schema: any;
}>;
export declare const fetchSecretsEndEnvironmentsList: (context: DefinitionMachineContext) => Promise<{
    secrets: any;
    envs: Record<string, string>;
}>;
export declare const refetchCurrentWorkflowVersionsService: ({ authHeaders: headers, workflowName, }: DefinitionMachineContext) => Promise<{
    versions?: undefined;
} | {
    versions: number[];
}>;

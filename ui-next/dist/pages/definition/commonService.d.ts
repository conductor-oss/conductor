import { HasAuthHeaders } from "types/common";
import { AUTH_HEADER_NAME } from "utils";
export declare const refetchAllWorkflowDefinitions: ({ authHeaders: headers, }: HasAuthHeaders) => Promise<any>;
export declare const getWorkflowDefinitionByNameAndVersion: ({ name, version, authHeaders: headers, }: {
    name: string;
    version: number;
    authHeaders: {
        [AUTH_HEADER_NAME]?: string;
    };
}) => Promise<any>;
export declare const getEnvVariables: ({ authHeaders: headers, }: HasAuthHeaders) => Promise<Record<string, string>>;

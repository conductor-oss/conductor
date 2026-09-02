import { IntegrationDef, IntegrationI } from "types";
type GetIntegrationsProps = {
    category?: string;
    activeOnly?: boolean;
};
export declare const useGetIntegration: ({ activeOnly, ...restProps }: GetIntegrationsProps) => import("react-query").UseQueryResult<IntegrationI[], unknown>;
export declare const useGetIntegrationDef: () => import("react-query").UseQueryResult<IntegrationDef[], unknown>;
export {};

import { IntegrationCategory } from "types/Integrations";
export declare function useIntegrationProviders({ category, activeOnly, }: {
    category: IntegrationCategory;
    activeOnly: boolean;
}): import("react-query").UseQueryResult<any, any>;

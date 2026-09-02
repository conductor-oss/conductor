/**
 * Mock feature flags utility
 */
export declare const createMockFeatureFlags: (enabledFeatures?: string[]) => {
    isEnabled: import("vitest").Mock<(feature: string) => boolean>;
    getValue: import("vitest").Mock<(feature: string, defaultValue?: string) => string | undefined>;
    getContextValue: import("vitest").Mock<() => undefined>;
};
/**
 * Common feature flag configurations for testing
 */
export declare const FEATURE_FLAG_SCENARIOS: {
    PLAYGROUND_ENABLED: "PLAYGROUND"[];
    GET_STARTED_ENABLED: "SHOW_GET_STARTED_PAGE"[];
    TASK_INDEXING_ENABLED: "TASK_INDEXING"[];
    ALL_FEATURES_ENABLED: ("PLAYGROUND" | "SCHEDULER" | "HUMAN_TASK" | "INTEGRATIONS" | "SECRETS" | "WEBHOOKS" | "RBAC" | "TASK_INDEXING" | "SHOW_GET_STARTED_PAGE" | "REMOTE_SERVICES")[];
    NO_FEATURES_ENABLED: never[];
};
/**
 * Mock all page components with consistent test IDs
 */
export declare const mockPageComponents: () => void;
/**
 * Helper to find routes in the route tree
 */
export declare const findRouteByPath: (routes: any[], path: string) => any;
/**
 * Helper to find all routes with a specific property
 */
export declare const findRoutesByProperty: (routes: any[], property: string, value?: any) => any[];
/**
 * Helper to get all paths from route tree
 */
export declare const getAllPaths: (routes: any[]) => string[];
/**
 * Helper to count routes at each level
 */
export declare const getRouteStats: (routes: any[]) => {
    totalRoutes: number;
    dynamicRoutes: number;
    wildcardRoutes: number;
    indexRoutes: number;
};

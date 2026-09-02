import { BaseTaskMenuItem, RichAddMenuTabs } from "./state/types";
export declare const itemMatchesSelectedTask: (item: BaseTaskMenuItem, selectedTab: RichAddMenuTabs) => boolean;
export declare const itemNameIncludesText: (item: BaseTaskMenuItem, searchQuery: string) => boolean;
export declare const itemFilterMatcher: (searchQuery: string, selectedTab: RichAddMenuTabs) => (item: BaseTaskMenuItem) => boolean;
interface JSONSchemaProperty {
    type: string;
    properties?: Record<string, JSONSchemaProperty>;
    items?: JSONSchemaProperty;
    required?: string[];
    enum?: any[];
    default?: any;
    minimum?: number;
    maximum?: number;
    description?: string;
    additionalProperties?: boolean;
    $schema?: string;
}
interface JSONSchema extends JSONSchemaProperty {
    type: string;
    properties?: Record<string, JSONSchemaProperty>;
    required?: string[];
}
export declare const generateObjectFromSchema: (schema: JSONSchema) => any;
export {};

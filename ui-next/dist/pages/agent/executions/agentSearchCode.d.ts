export type BuildQueryOutput = {
    query: string;
    freeText: string;
    start: number;
    size: number;
    sort: string;
    classifier: string;
    topLevelOnly: boolean;
};
/**
 * Builds a search URL that preserves the filters applied on the Agent Executions page.
 */
export declare const buildEndpoint: ({ start, size, sort, freeText, query, classifier, topLevelOnly, }: BuildQueryOutput) => string;

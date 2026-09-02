/**
 * Search helpers for the core OSS search machine.
 *
 * This file handles fuzzy search and result formatting for the core OSS
 * searchable categories: workflows, task definitions, schedulers, and events.
 *
 * Enterprise categories (users, groups, applications, webhooks, integrations,
 * prompts, user forms) are handled by enterprise plugins via searchProviders.
 */
import { MenuItemType } from "components/providers/sidebar/types";
import { CommonDef } from "./types";
import { WorkflowDef } from "types/WorkflowDef";
export interface SearchResultExtractorProps {
    taskDefinitions?: CommonDef[];
    workflowDefinitions?: WorkflowDef[];
    scheduler?: string[];
    events?: string[];
    searchTerm: string;
    maxSearchResults?: number;
}
export declare const searchFunction: (targets: CommonDef[] | string[], searchTerm: string, maxSearchResults?: number, keys?: string[]) => (string | CommonDef)[];
export declare const searchResultExtractor: ({ taskDefinitions, workflowDefinitions, scheduler, events, searchTerm, maxSearchResults, }: SearchResultExtractorProps) => {
    title: string;
    route: string;
    sub: {
        route: string;
        title: string;
    }[];
}[] | null;
export declare const flattenMenu: (menuItems: MenuItemType[], parentTitle?: string) => {
    route: string;
    title: string;
}[];

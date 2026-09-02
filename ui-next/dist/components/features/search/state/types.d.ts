import { ReactElement } from "react";
import { AuthHeaders, WorkflowDef } from "types";
export type SearchResultBase = {
    icon?: ReactElement;
    title: string;
    route?: string;
};
type SearchResultRoute = SearchResultBase & {
    sub?: never;
};
type SearchResultSub = SearchResultBase & {
    sub: SearchResults;
};
export type SearchResultItem = SearchResultRoute | SearchResultSub;
export type SearchResults = Array<SearchResultItem>;
export type CommonDef = {
    name: string;
    description?: string;
    id?: string;
    version?: string | number;
};
export declare enum SearchMachineStates {
    INIT = "INIT",
    FETCHER = "FETCHER",
    FETCH_TASK_DEFINITIONS = "FETCH_TASK_DEFINITIONS",
    FETCH_WF_DEFINITIONS = "FETCH_WF_DEFINITIONS",
    FETCH_SCHEDULERS = "FETCH_SCHEDULERS",
    FETCH_EVENTS = "FETCH_EVENTS",
    FETCH_PLUGIN_DATA = "FETCH_PLUGIN_DATA",
    FILTER = "FILTER",
    WAIT = "WAIT",
    FILTERING = "FILTERING"
}
type Error = {
    message: string;
    severity: string;
};
export interface SearchMachineContext {
    authHeaders?: AuthHeaders;
    taskDefinitions: CommonDef[];
    workflowDefinitions: WorkflowDef[];
    schedulers: string[];
    events: string[];
    pluginData: Record<string, any[]>;
    searchTerm: string;
    error?: Error;
    maxSearchResults?: number;
}
export declare enum SearchActionTypes {
    UPDATE_SEARCH_TERM = "UPDATE_SEARCH_TERM"
}
export type PersistSearchTermEvent = {
    type: SearchActionTypes.UPDATE_SEARCH_TERM;
    searchTerm: string;
    count?: number;
};
export type HookActions = {
    setSearchTerm: (value: string, max?: number) => void;
};
export type HookState = {
    searchTerm: string;
    searchResults: SearchResults | null;
};
export type SearchMachineEvents = PersistSearchTermEvent;
export {};

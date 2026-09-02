/**
 * Core OSS search service fetchers.
 *
 * These fetch workflow definitions, task definitions, schedulers, and event
 * handlers — all of which are core OSS features.
 *
 * Enterprise search categories (users, groups, applications, webhooks,
 * integrations, prompts, user forms) are registered by enterprise plugins
 * via the plugin registry's searchProviders mechanism.
 */
import { SearchMachineContext } from "./types";
export declare const fetchForTaskNames: ({ authHeaders: headers, taskDefinitions, }: SearchMachineContext) => Promise<unknown[]>;
export declare const fetchForWorkflowDef: ({ authHeaders: headers, workflowDefinitions, }: SearchMachineContext) => Promise<unknown[]>;
export declare const fetchForScheduleNames: ({ authHeaders: headers, schedulers, }: SearchMachineContext) => Promise<unknown[]>;
export declare const fetchForEventNames: ({ authHeaders: headers, events, }: SearchMachineContext) => Promise<unknown[]>;

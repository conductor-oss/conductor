import { WorkflowDef } from "types/WorkflowDef";
import { CommonTaskDef } from "types/TaskType";
/**
 * Get unique workflows with latest version
 * @param workflows WorkflowDef[]
 * @returns WorkflowDef[]
 */
export declare const getUniqueWorkflows: (workflows: WorkflowDef[]) => any[];
/**
 * Get unique workflows with versions
 * @param workflows WorkflowDef[]
 * @returns Map<string, number[]>
 */
export declare const getUniqueWorkflowsWithVersions: (workflows?: WorkflowDef[]) => Map<string, number[]>;
export declare function mapWalk(tasks: CommonTaskDef[], fn: (task: CommonTaskDef) => CommonTaskDef | null): CommonTaskDef[];
export declare function flatten(tasks: CommonTaskDef[]): CommonTaskDef[];
export declare function filterTasks(tasks: CommonTaskDef[], predicate: (task: CommonTaskDef) => boolean): CommonTaskDef[];
export declare const handlebarsMatcherExtractor: (val: any, matcher: (val: string) => boolean) => string[];
export type NameVersion = {
    name: string;
    version?: string;
};
/**
 * Walks through all available tasks in search for dependencies
 *
 * @param tasks wokflow tasks
 * @returns
 */
export declare function scanTasksForDependenciesInTasks(tasks: CommonTaskDef[]): {
    readonly integrationNames: string[];
    readonly promptNames: string[];
    readonly userFormsNameVersion: NameVersion[];
    readonly schemas: NameVersion[];
    readonly secrets: string[];
    readonly env: string[];
};
export declare function scanTasksForDependenciesInWorkflow(workflow: WorkflowDef): {
    schemas: NameVersion[];
    secrets: string[];
    env: string[];
    workflowName: string;
    workflowVersion: number;
    integrationNames: string[];
    promptNames: string[];
    userFormsNameVersion: NameVersion[];
};
export declare const replaceIntegrationName: (task: CommonTaskDef, originalName: string, replaceName: string) => CommonTaskDef;

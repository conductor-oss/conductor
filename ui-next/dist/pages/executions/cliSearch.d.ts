import type { BuildQueryOutput } from "./ApiSearchModalIntegration";
export declare const buildWorkflowSearchCli: ({ freeText, query, size, ...pagination }: BuildQueryOutput) => string;
export declare const buildSchedulerSearchCli: ({ query, size, ...search }: BuildQueryOutput) => string;
export declare const buildTaskSearchCli: () => string;

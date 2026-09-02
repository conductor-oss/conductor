import { NodeTaskData } from "components/features/flow/nodes/mapper";
export declare const shouldHide: ({ status, withinExpandedSubWorkflow, }?: Partial<NodeTaskData>) => boolean;
export declare function dowhileHasAllIterationsInOutput(outputData: Record<string, unknown>): boolean;
/**
 * Returns true when the backend has replaced old iteration payloads with a
 * lightweight sentinel ({"_summarized": true}) to keep the response small.
 * All iteration keys are still present so the dropdown can enumerate them,
 * but the full output data is only available for the most recent iterations.
 */
export declare function dowhileHasSummarizedIterations(outputData: Record<string, unknown>): boolean;
export declare function showIterationChip(nodeData: NodeTaskData): boolean;
export declare const isValidUri: (uriString: string) => boolean;

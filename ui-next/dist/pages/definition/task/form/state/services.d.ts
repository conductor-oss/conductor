import { TaskDefinitionFormContext } from "pages/definition/task/form/state/types";
import { handleDownloadFile } from "pages/definition/task/state/services";
export declare const validateForm: ({ modifiedTaskDefinition, }: TaskDefinitionFormContext) => Promise<{
    error: {};
    numberOfError: number;
}>;
export { handleDownloadFile };

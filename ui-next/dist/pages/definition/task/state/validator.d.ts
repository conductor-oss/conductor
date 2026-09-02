import type { ErrorObject } from "ajv";
import { TaskDefinitionDto } from "types/TaskDefinition";
export declare const validateTask: (task: TaskDefinitionDto | TaskDefinitionDto[], isBulk: boolean) => null | ErrorObject[];
export declare const validatingService: (modifiedTaskDefinition: TaskDefinitionDto | TaskDefinitionDto[], isBulk: boolean) => Promise<{
    error: {};
    numberOfError: number;
}>;

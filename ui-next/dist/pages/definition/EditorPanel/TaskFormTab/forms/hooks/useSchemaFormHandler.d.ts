import { SchemaFormPropsValue } from "../SchemaForm";
import { TaskFormProps } from "../types";
/**
 * Custom hook that handles schema form changes and automatically populates
 * inputParameters from schema defaults when appropriate.
 *
 * @param props - TaskFormProps containing task and onChange
 * @returns A handler function for SchemaForm onChange events
 */
export declare const useSchemaFormHandler: ({ task, onChange }: TaskFormProps) => (schema?: SchemaFormPropsValue) => Promise<void>;

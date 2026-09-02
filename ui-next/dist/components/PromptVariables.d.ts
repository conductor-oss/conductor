import { TaskDef } from "types";
type PromptVariablesProps = {
    currentVariables: string | Record<string, string>;
    onChange: (t: Partial<TaskDef>) => void;
    updateField: (path: string, value: unknown, task: Partial<TaskDef>) => Partial<TaskDef>;
    task: Partial<TaskDef>;
};
declare const PromptVariables: ({ currentVariables, onChange, updateField, task, }: PromptVariablesProps) => import("react").JSX.Element;
export default PromptVariables;

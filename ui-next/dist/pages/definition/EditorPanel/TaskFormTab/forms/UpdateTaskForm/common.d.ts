import { ChangeEvent } from "react";
import { TaskFormProps } from "../types";
export declare const useUpdateTaskHandler: ({ task, onChange }: TaskFormProps) => {
    handleTaskStatusChange: (value: string) => any;
    handleMergeOutputChange: (event: ChangeEvent<HTMLInputElement>) => void;
};

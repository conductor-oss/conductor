import { ChangeEvent } from "react";
import { updateField } from "utils/fieldHelpers";
import { TaskFormProps } from "../types";

export const useUpdateTaskHandler = ({ task, onChange }: TaskFormProps) => {
  const handleTaskStatusChange = (value: string) =>
    onChange(updateField("inputParameters.taskStatus", value, task));

  const handleMergeOutputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const isChecked = event.target.checked;
    onChange(updateField("inputParameters.mergeOutput", isChecked, task));
  };

  return {
    handleTaskStatusChange,
    handleMergeOutputChange,
  };
};

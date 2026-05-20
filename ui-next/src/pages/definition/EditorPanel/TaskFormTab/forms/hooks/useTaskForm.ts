import _path from "lodash/fp/path";

import { updateField } from "utils/fieldHelpers";
import { TaskFormProps } from "../types";

export const useTaskForm = (
  path: string,
  { task, onChange }: TaskFormProps,
) => {
  const value = _path(path, task);

  const setValue = (v: any) => onChange(updateField(path, v, task));

  return [value, setValue];
};

import _get from "lodash/get";
import _clone from "lodash/clone";
import { updateField } from "utils/fieldHelpers";
import { TaskFormProps } from "./types";

export const useGetSetHandler = (
  { task, onChange }: TaskFormProps,
  path: string,
) =>
  [
    _clone(_get(task, path)),
    (val: any) => onChange(updateField(path, val, task)),
  ] as const;

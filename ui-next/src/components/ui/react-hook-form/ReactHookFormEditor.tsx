import {
  FieldValues,
  UseControllerProps,
  useController,
} from "react-hook-form";
import { Editor, EditorProps } from "@monaco-editor/react";

type ReactHookFormEditorProps<T> = EditorProps &
  UseControllerProps<T extends FieldValues ? T : FieldValues>;

export default function ReactHookFormEditor<T>({
  // Controller props
  control,
  name,
  rules,
  shouldUnregister,
  defaultValue,

  // Editor props
  ...props
}: ReactHookFormEditorProps<T>) {
  const {
    field: { onChange, ...fieldProps },
  } = useController({
    control,
    name,
    rules,
    shouldUnregister,
    defaultValue,
  });
  return (
    <Editor
      {...fieldProps}
      {...props}
      onChange={(value, event) => {
        if (typeof value === "string") {
          onChange(value, event);
          props?.onChange?.(value, event);
        }
      }}
    />
  );
}

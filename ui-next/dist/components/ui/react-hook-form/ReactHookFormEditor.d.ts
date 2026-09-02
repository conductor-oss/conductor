import { FieldValues, UseControllerProps } from "react-hook-form";
import { EditorProps } from "@monaco-editor/react";
type ReactHookFormEditorProps<T> = EditorProps & UseControllerProps<T extends FieldValues ? T : FieldValues>;
export default function ReactHookFormEditor<T>({ control, name, rules, shouldUnregister, defaultValue, ...props }: ReactHookFormEditorProps<T>): import("react").JSX.Element;
export {};

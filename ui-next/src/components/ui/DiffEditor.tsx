import { DiffEditor as MonacoDiffEditor } from "@monaco-editor/react";
import { type DiffEditorOptions } from "shared/editor";
import "./diff-editor.css";

const defaultOptions: DiffEditorOptions = {
  useInlineViewWhenSpaceIsLimited: false,
  renderGutterMenu: false,
  scrollbar: {
    vertical: "visible",
    horizontal: "hidden",
  },
};

export const DiffEditor = ({ options = {}, ...rest }) => {
  return (
    <MonacoDiffEditor options={{ ...defaultOptions, ...options }} {...rest} />
  );
};

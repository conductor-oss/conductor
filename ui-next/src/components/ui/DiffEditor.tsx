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
    <MonacoDiffEditor
      // Keep the underlying text models alive across unmount. The diff editor is
      // toggled off when a confirm-save flow closes (e.g. after the backend
      // rejects a save); without this, Monaco disposes the models while the
      // widget still references them and throws the uncaught
      // "TextModel got disposed before DiffEditorWidget model got reset".
      keepCurrentOriginalModel
      keepCurrentModifiedModel
      options={{ ...defaultOptions, ...options }}
      {...rest}
    />
  );
};

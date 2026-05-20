import { editor } from "monaco-editor";

export type EditorOptions = editor.IStandaloneEditorConstructionOptions;
export type DiffEditorOptions = editor.IDiffEditorConstructionOptions;
export { editor };

export const defaultEditorOptions: EditorOptions = {
  stickyScroll: {
    enabled: false,
  },
};

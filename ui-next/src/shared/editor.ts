// Types only: a value import of "monaco-editor" bundles all of Monaco (~9.6 MB
// minified) into every consumer's main chunk. The editor itself loads from the
// CDN at runtime (see monacoLoader.ts), so nothing here may need Monaco's code.
import type { editor } from "monaco-editor";

export type EditorOptions = editor.IStandaloneEditorConstructionOptions;
export type DiffEditorOptions = editor.IDiffEditorConstructionOptions;
export type { editor };

/**
 * editor.ShowLightbulbIconMode, which is a string enum. Cast so it still
 * type-checks against Monaco's option types.
 */
export const ShowLightbulbIconMode = {
  Off: "off" as editor.ShowLightbulbIconMode.Off,
  OnCode: "onCode" as editor.ShowLightbulbIconMode.OnCode,
  On: "on" as editor.ShowLightbulbIconMode.On,
} as const;

export const defaultEditorOptions: EditorOptions = {
  stickyScroll: {
    enabled: false,
  },
};

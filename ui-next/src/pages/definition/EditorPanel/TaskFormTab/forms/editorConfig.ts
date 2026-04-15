import { editor, type EditorOptions } from "shared/editor";

export const smallEditorDefaultOptions: EditorOptions = {
  tabSize: 2,
  minimap: { enabled: false },
  lightbulb: { enabled: editor.ShowLightbulbIconMode.Off },
  quickSuggestions: true,
  lineNumbers: "off",
  glyphMargin: false,
  folding: false,
  // Undocumented see https://github.com/Microsoft/vscode/issues/30795#issuecomment-410998882
  lineDecorationsWidth: 0,
  lineNumbersMinChars: 0,
  renderLineHighlight: "none",
  overviewRulerLanes: 0,
  hideCursorInOverviewRuler: true,
  scrollbar: {
    vertical: "hidden",
    // this property is added because it was not allowing us to scroll when mouse pointer is over this component
    alwaysConsumeMouseWheel: false,
  },
  overviewRulerBorder: false,
  automaticLayout: true,
};

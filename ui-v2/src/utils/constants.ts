export const ROWS_PER_PAGE_OPTIONS = [
  "15",
  "30",
  "50",
  "100",
  "200",
  "300",
  "500",
] as const;

export const MS_IN_DAY = 86400000 as const;

export const DEFAULT_WF_ATTRIBUTES = [
  "workflow.workflowId",
  "workflow.output",
  "workflow.status",
  "workflow.parentWorkflowId",
  "workflow.parentWorkflowTaskId",
  "workflow.workflowType",
  "workflow.version",
  "workflow.correlationId",
  "workflow.variables",
  "workflow.createTime",
  "workflow.taskToDomain",
] as const;

import { editor, type EditorOptions } from "shared/editor";

export const SMALL_EDITOR_DEFAULT_OPTIONS: EditorOptions = {
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

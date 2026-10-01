import Editor, { Monaco } from "@monaco-editor/react";
import { Box, Button, Theme } from "@mui/material";
import {
  BracketsCurly,
  CheckCircle,
  Copy,
  DownloadSimple,
  Warning,
  WarningCircle,
  XCircle,
} from "@phosphor-icons/react";
import { DiffEditor } from "components/ui/DiffEditor";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import {
  ReactNode,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { defaultEditorOptions, EditorOptions, editor } from "shared/editor";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import {
  createJsonValidator,
  JsonValidator,
  ProblemSeverity,
  summarizeProblems,
  ValidationStatus,
} from "./jsonValidation";

export type CodeTabEditorProps<T extends Record<string, any>> = {
  value: string;
  onChange: (text: string) => void;
  /**
   * Domain checks shown in the status pill and as editor markers. Build one
   * with `createJsonValidator`; defaults to "must be a JSON object".
   */
  validate?: JsonValidator<T>;
  /**
   * When set, shows a read-only diff of `value` against `original` (e.g. while
   * confirming a save) and hides the editing actions.
   */
  diff?: { original: string } | null;
  /** Download filename without `.json`; may derive from the parsed document. */
  downloadName?: string | ((parsed: T | null) => string | undefined);
  /** Right-hand status bar label, e.g. "JSON · EventHandler schema". */
  schemaLabel?: string;
  /** Prefix for element ids, so tests and e2e can target a specific tab. */
  idPrefix?: string;
  /** Extra toolbar controls rendered before Format / Copy / Download. */
  toolbarActions?: ReactNode;
  beforeMount?: (monaco: Monaco) => void;
  onMount?: (editor: editor.IStandaloneCodeEditor, monaco: Monaco) => void;
  editorOptions?: EditorOptions;
};

const MARKER_OWNER = "code-tab";
const TAB_SIZE = 2;

const defaultValidate = createJsonValidator();

const statusIcon: Record<ValidationStatus["kind"], typeof CheckCircle> = {
  invalid: XCircle,
  error: WarningCircle,
  warn: Warning,
  valid: CheckCircle,
};

// Light values follow the design tokens; dark mode swaps to the lighter end of
// each ramp so the chip stays legible on the dark editor chrome.
const statusColors = (
  kind: ValidationStatus["kind"],
  isDark: boolean,
): { fg: string; bg: string } => {
  switch (kind) {
    case "invalid":
    case "error":
      return isDark
        ? { fg: colors.red10, bg: "rgba(229, 9, 20, 0.16)" }
        : { fg: colors.red05, bg: colors.red13 };
    case "warn":
      return isDark
        ? { fg: colors.orange10, bg: "rgba(246, 110, 19, 0.16)" }
        : { fg: colors.orange04, bg: colors.orange13 };
    default:
      return isDark
        ? { fg: colors.lime10, bg: "rgba(65, 185, 87, 0.16)" }
        : { fg: colors.lime05, bg: colors.lime13 };
  }
};

const barStyle = {
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  flexWrap: "wrap",
  gap: 3,
};

// Set colour in the shorthand itself: a bare `1px solid` resets the colour to
// currentColor (black), overriding any separate borderColor.
const dividerBorder = (theme: Theme) => `1px solid ${theme.palette.divider}`;

const monoStyle = {
  fontFamily:
    "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
};

// Ghost buttons. The theme's text variants deliberately unset the hover
// background, so restate it here or the buttons give no hover feedback.
const toolbarButtonSx = {
  color: "text.primary",
  fontWeight: 400,
  minWidth: 0,
  px: 2,
  "&:hover": {
    color: "primary.main",
    backgroundColor: "action.hover",
  },
  "&.Mui-disabled": {
    color: "text.disabled",
  },
};

/**
 * A full-height JSON Code tab: validation status and Format / Copy / Download
 * above a Monaco editor, caret and line info below. Fills its parent, so give
 * the parent a height and no scrolling of its own — Monaco scrolls inside.
 */
export const CodeTabEditor = <T extends Record<string, any>>({
  value,
  onChange,
  validate = defaultValidate as unknown as JsonValidator<T>,
  diff,
  downloadName = "document",
  schemaLabel = "JSON",
  idPrefix = "code-tab",
  toolbarActions,
  beforeMount,
  onMount,
  editorOptions,
}: CodeTabEditorProps<T>) => {
  const { mode } = useContext(ColorModeContext);
  const isDark = mode === "dark";
  const editorTheme = isDark ? "vs-dark" : "vs-light";

  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<Monaco | null>(null);
  const [caret, setCaret] = useState({ ln: 1, col: 1 });
  const [notice, setNotice] = useState<{
    message: string;
    severity: "success" | "error";
    at: number;
  } | null>(null);
  const [isMounted, setIsMounted] = useState(false);

  const analysis = useMemo(() => validate(value), [validate, value]);
  const status = summarizeProblems(analysis);
  const lineCount = value.split("\n").length;

  // Mirror the domain checks into Monaco so they show as squiggles and
  // gutter/overview-ruler marks alongside Monaco's own JSON syntax errors.
  // Parse errors are left to Monaco, which pinpoints them better than we can.
  useEffect(() => {
    const monaco = monacoRef.current;
    const model = editorRef.current?.getModel();
    if (!monaco || !model) return;
    const severity: Record<ProblemSeverity, number> = {
      error: monaco.MarkerSeverity.Error,
      warn: monaco.MarkerSeverity.Warning,
      info: monaco.MarkerSeverity.Info,
    };
    const markers = analysis.parsed
      ? analysis.problems
          .filter((p) => p.line <= model.getLineCount())
          .map((p) => ({
            severity: severity[p.sev],
            message: p.msg,
            startLineNumber: p.line,
            startColumn: model.getLineFirstNonWhitespaceColumn(p.line) || 1,
            endLineNumber: p.line,
            endColumn: model.getLineMaxColumn(p.line),
          }))
      : [];
    monaco.editor.setModelMarkers(model, MARKER_OWNER, markers);
  }, [analysis, isMounted]);

  const handleMount = (ed: editor.IStandaloneCodeEditor, monaco: Monaco) => {
    editorRef.current = ed;
    monacoRef.current = monaco;
    setIsMounted(true);
    ed.onDidChangeCursorPosition((e) => {
      setCaret({ ln: e.position.lineNumber, col: e.position.column });
    });
    onMount?.(ed, monaco);
  };

  const handleFormat = () => {
    if (!analysis.parsed) return;
    const formatted = JSON.stringify(analysis.parsed, null, TAB_SIZE);
    const ed = editorRef.current;
    const model = ed?.getModel();
    if (ed && model) {
      // Go through an edit rather than swapping the value so Cmd+Z undoes it.
      ed.pushUndoStop();
      ed.executeEdits("format", [
        { range: model.getFullModelRange(), text: formatted },
      ]);
      ed.pushUndoStop();
    } else {
      onChange(formatted);
    }
    setNotice({
      message: formatted === value ? "Already formatted" : "Formatted",
      severity: "success",
      at: Date.now(),
    });
  };

  const handleCopy = () => {
    navigator.clipboard
      ?.writeText(value)
      .then(() =>
        setNotice({
          message: "Copied to clipboard",
          severity: "success",
          at: Date.now(),
        }),
      )
      .catch(() =>
        setNotice({
          message: "Copy failed",
          severity: "error",
          at: Date.now(),
        }),
      );
  };

  const handleDownload = () => {
    const name =
      (typeof downloadName === "function"
        ? downloadName(analysis.parsed)
        : downloadName) || "document";
    const url = URL.createObjectURL(
      new Blob([value], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = `${name}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const StatusIcon = statusIcon[status.kind];
  const chip = statusColors(status.kind, isDark);

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        backgroundColor: (theme) => theme.palette.background.paper,
      }}
    >
      <Box
        id={`${idPrefix}-toolbar`}
        sx={{ ...barStyle, pl: 5, pr: 3, py: 2, borderBottom: dividerBorder }}
      >
        <Box
          component="span"
          id={`${idPrefix}-status`}
          data-status={status.kind}
          sx={{
            display: "inline-flex",
            alignItems: "center",
            gap: 1.5,
            fontSize: 12,
            px: 2,
            py: 0.5,
            borderRadius: 1,
            color: chip.fg,
            backgroundColor: chip.bg,
          }}
        >
          <StatusIcon size={13} weight="bold" />
          {status.label}
        </Box>
        {!diff && (
          <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            {toolbarActions}
            <Button
              variant="text"
              size="small"
              sx={toolbarButtonSx}
              startIcon={<BracketsCurly size={16} />}
              disabled={!analysis.parsed}
              onClick={handleFormat}
              id={`${idPrefix}-format`}
            >
              Format
            </Button>
            <Button
              variant="text"
              size="small"
              sx={toolbarButtonSx}
              startIcon={<Copy size={16} />}
              onClick={handleCopy}
              id={`${idPrefix}-copy`}
            >
              Copy
            </Button>
            <Button
              variant="text"
              size="small"
              sx={toolbarButtonSx}
              startIcon={<DownloadSimple size={16} />}
              onClick={handleDownload}
              id={`${idPrefix}-download`}
            >
              Download
            </Button>
          </Box>
        )}
      </Box>

      <Box sx={{ flex: 1, minHeight: 0, overflowX: "auto" }}>
        <Box sx={{ height: "100%", minWidth: 590 }}>
          {diff ? (
            <DiffEditor
              height={"100%"}
              width={"100%"}
              language="json"
              original={diff.original}
              modified={value}
              theme={editorTheme}
            />
          ) : (
            <Editor
              height="100%"
              width="100%"
              language="json"
              theme={editorTheme}
              value={value}
              beforeMount={beforeMount}
              onMount={handleMount}
              options={{
                ...defaultEditorOptions,
                selectOnLineNumbers: true,
                tabSize: TAB_SIZE,
                fontSize: 13,
                lineHeight: 21,
                padding: { top: 8, bottom: 8 },
                minimap: { enabled: false },
                ...editorOptions,
              }}
              onChange={(maybeText) => {
                if (typeof maybeText === "string") onChange(maybeText);
              }}
            />
          )}
        </Box>
      </Box>

      <Box
        id={`${idPrefix}-statusbar`}
        sx={{
          ...barStyle,
          ...monoStyle,
          flexWrap: "nowrap",
          gap: 4,
          minHeight: 28,
          px: 5,
          borderTop: dividerBorder,
          fontSize: 11,
          color: "text.secondary",
          backgroundColor: (theme) =>
            theme.palette.mode === "dark" ? colors.gray01 : colors.gray14,
        }}
      >
        <Box sx={{ display: "flex", gap: 4 }}>
          <span>
            Ln {caret.ln}, Col {caret.col}
          </span>
          <span>{lineCount} lines</span>
          <span>Spaces: {TAB_SIZE}</span>
        </Box>
        <span>{schemaLabel}</span>
      </Box>
      {notice && (
        <SnackbarMessage
          key={notice.at}
          id={`${idPrefix}-notice`}
          message={notice.message}
          severity={notice.severity}
          onDismiss={() => setNotice(null)}
          anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        />
      )}
    </Box>
  );
};

export default CodeTabEditor;

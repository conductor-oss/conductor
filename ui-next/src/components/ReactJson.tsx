import Editor, { Monaco } from "@monaco-editor/react";
import { Box, Paper, Tooltip } from "@mui/material";
import {
  CornersOut,
  Download,
  List,
  ListPlus,
  PencilSimple,
  XCircle,
} from "@phosphor-icons/react";
import Button from "components/ui/buttons/MuiButton";
import SaveIcon from "components/icons/SaveIcon";
import XCloseIcon from "components/icons/XCloseIcon";
import { CSSProperties, Suspense, useContext, useRef, useState } from "react";
import { defaultEditorOptions, type EditorOptions } from "shared/editor";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import { tryToJson } from "utils/utils";

const DARK_BACKGROUND = "#111111";
const COLLAPSE_IDLE = "COLLAPSE_IDLE";
const COLLAPSE_EXPAND = "COLLAPSE_EXPAND";
const COLLAPSE_COLLAPSE = "COLLAPSE_COLLAPSE";

export interface ReactJSONProps {
  src: any;
  title?: string;
  className?: string;
  style?: CSSProperties;
  showIconText?: boolean;
  workflowName?: string;
  editorHeight?: string;
  item?: any;
  handleFullScreen?: (item: any) => void;
  fullScreen?: any;
  customOptions?: object;
  overflowX?: string;
  overflowY?: string;
  isEditable?: boolean;
  handleUpdate?: (value: string) => void;
}

const editorOptions: EditorOptions = {
  ...defaultEditorOptions,
  tabSize: 2,
  readOnly: true,
  quickSuggestions: true,
  folding: true,
  automaticLayout: true,
  scrollbar: {
    // this property is added because it was not allowing us to scroll when mouse pointer is over this component
    alwaysConsumeMouseWheel: false,
  },
  wordWrap: "on",
};

export default function ReactJson({
  title,
  className = "",
  style,
  showIconText = true,
  editorHeight = "500px",
  handleFullScreen,
  item,
  fullScreen,
  customOptions,
  overflowX,
  overflowY,
  isEditable,
  handleUpdate,
  ...props
}: ReactJSONProps) {
  const editorRef = useRef<Monaco>(null);

  const [collapse, setCollapse] = useState(COLLAPSE_EXPAND);
  const [editEnabled, setEditEnabled] = useState(false);
  const [isJsonParsable, setIsJsonParsable] = useState(true);
  const colorModeContext = useContext(ColorModeContext);
  let mode = "light";
  if (colorModeContext && colorModeContext.mode) {
    mode = colorModeContext.mode;
  }

  const handleFoldAll = () => {
    const editor = editorRef.current;
    if (editor) {
      const foldAction = editor.getAction("editor.foldAll");
      foldAction.run();
    }
  };

  const handleUnfoldAll = () => {
    const editor = editorRef.current;
    if (editor) {
      const unfoldAction = editor.getAction("editor.unfoldAll");
      unfoldAction.run();
    }
  };

  const toggleCollapse = () => {
    const shouldExpand = [COLLAPSE_IDLE, COLLAPSE_COLLAPSE].includes(collapse);

    if (shouldExpand) {
      handleUnfoldAll();
      setCollapse(COLLAPSE_EXPAND);
    } else {
      handleFoldAll();
      setCollapse(COLLAPSE_COLLAPSE);
    }
  };

  const toggleDownload = () => {
    const a = window.document.createElement("a");
    a.href = window.URL.createObjectURL(
      new Blob([JSON.stringify(props.src, null, 2)], {
        type: "application/json",
      }),
    );
    a.download = `${props.workflowName}_${title}.json`;

    // Append anchor to body.
    document.body.appendChild(a);
    a.click();

    // Remove anchor from body
    document.body.removeChild(a);
  };

  const toggleFullscreen = () => {
    if (handleFullScreen && item) {
      handleFullScreen(item);
    }
  };

  const collapseButtonText =
    collapse === COLLAPSE_IDLE || collapse === COLLAPSE_COLLAPSE
      ? "Expand all"
      : "Collapse all";

  const handleEditorWillMount = (monaco: Monaco) => {
    monaco.editor.defineTheme("vs-light", {
      base: "vs",
      inherit: true,
      rules: [
        {
          token: "number",
          foreground: colors.primaryGreen,
        },
      ],
      colors: {},
    });
  };

  const handleEditorMount = (editor: Monaco) => {
    editorRef.current = editor;
  };

  const mainStyle: object = {
    ...style,
    ...(overflowX && { overflowX: overflowX }),
    ...(overflowY && { overflowY: overflowY }),
  };

  const handleEnableEdit = (value: boolean) => {
    setEditEnabled(value);
  };

  const onEditorChange = () => {
    const editorValue = editorRef?.current?.getValue();
    const tryJson = tryToJson(editorValue);
    if (tryJson) {
      setIsJsonParsable(true);
    } else {
      setIsJsonParsable(false);
    }
  };

  const handleSave = () => {
    const editorValue = editorRef?.current?.getValue();
    setEditEnabled(false);
    if (handleUpdate) {
      handleUpdate(editorValue);
    }
  };

  return (
    <Box
      className={className}
      style={mainStyle}
      sx={{
        marginBottom: 3,
        height: "100%",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <Box
        sx={{
          display: "flex",
          flexDirection: "row",
          alignItems: "center",
          justifyContent: "space-between",
          paddingBottom: "5px",
        }}
      >
        <Box
          sx={{ fontSize: 18, fontWeight: 600, paddingLeft: 3, minWidth: 75 }}
        >
          {title}
        </Box>

        <Box display={"flex"}>
          {isEditable && (
            <>
              {!editEnabled ? (
                <Tooltip title="Edit variables">
                  <Button
                    variant="text"
                    color="inherit"
                    onClick={() => handleEnableEdit(true)}
                    startIcon={<PencilSimple size={13} />}
                    size="small"
                    sx={{ fontSize: "10px" }}
                  >
                    Edit
                  </Button>
                </Tooltip>
              ) : (
                <Box display={"flex"} gap={1}>
                  <Button
                    variant="text"
                    color="inherit"
                    onClick={() => handleEnableEdit(false)}
                    startIcon={<XCloseIcon size={14} />}
                    size="small"
                    sx={{ fontSize: "10px" }}
                  >
                    Cancel
                  </Button>

                  <Button
                    variant="text"
                    color="inherit"
                    disabled={!isJsonParsable}
                    onClick={handleSave}
                    startIcon={<SaveIcon height={14} width={14} />}
                    size="small"
                    sx={{ fontSize: "10px" }}
                  >
                    Update
                  </Button>
                </Box>
              )}
            </>
          )}
          <Tooltip title="Download workflow JSON">
            <Button
              variant="text"
              color="inherit"
              onClick={() => toggleDownload()}
              startIcon={<Download size={13} />}
              size="small"
              sx={{ fontSize: "10px" }}
            >
              Download
            </Button>
          </Tooltip>

          <Tooltip
            title={
              collapse === COLLAPSE_EXPAND
                ? "Collapse JSON object"
                : "Expand JSON object (could be slow for large documents)"
            }
          >
            <Button
              onClick={toggleCollapse}
              size="small"
              variant="text"
              color="inherit"
              startIcon={
                collapse === COLLAPSE_EXPAND ? (
                  <List size={13} />
                ) : (
                  <ListPlus size={13} />
                )
              }
              sx={{ fontSize: "10px" }}
            >
              {showIconText ? collapseButtonText : null}
            </Button>
          </Tooltip>

          {fullScreen && (
            <Button
              variant="text"
              color="inherit"
              onClick={toggleFullscreen}
              startIcon={
                fullScreen.length > 0 ? <XCircle /> : <CornersOut size={13} />
              }
              size="small"
              sx={{ fontSize: "10px" }}
            >
              {fullScreen.length === 0 && "Fullscreen"}
            </Button>
          )}
        </Box>
      </Box>

      <Paper
        variant="elevation"
        elevation={1}
        sx={{
          flex: 1,
          padding: 3,
          background: mode === "dark" ? DARK_BACKGROUND : null,
          fontFamily: 'Menlo, Monaco, "Courier New", monospace',
        }}
      >
        <Suspense fallback={<div>Loading...</div>}>
          <Editor
            theme={mode === "dark" ? "vs-dark" : "vs-light"}
            width="100%"
            height={editorHeight}
            defaultLanguage="json"
            options={
              customOptions
                ? {
                    ...editorOptions,
                    ...customOptions,
                    readOnly: editEnabled ? false : true,
                  }
                : {
                    ...editorOptions,
                    readOnly: editEnabled ? false : true,
                  }
            }
            value={JSON.stringify(props.src, null, 2)}
            saveViewState
            onMount={handleEditorMount}
            beforeMount={handleEditorWillMount}
            onChange={onEditorChange}
          />
        </Suspense>
      </Paper>
    </Box>
  );
}

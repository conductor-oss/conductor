import React, { useRef } from "react";
import Editor from "@monaco-editor/react";
import { makeStyles } from "@material-ui/styles";
import { InputLabel, IconButton, Tooltip } from "@material-ui/core";
import clsx from "clsx";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import ExpandLessIcon from "@material-ui/icons/ExpandLess";
import FileCopyIcon from "@material-ui/icons/FileCopy";

const useStyles = makeStyles({
  monaco: {},
  outerWrapper: {
    height: "100%",
    display: "flex",
    flexDirection: "column",
    paddingTop: 15,
  },
  editorWrapper: {
    flex: 1,
    marginLeft: 10,
    position: "relative",
    minHeight: 0,
  },
  label: {
    marginTop: 13,
    marginBottom: 10,
    flex: 1,
  },
  toolbar: {
    paddingRight: 15,
    paddingLeft: 15,
    display: "flex",
    alignItems: "flex-start",
    flexDirection: "row",
  },
});

export default function ReactJson({
  className,
  label,
  src,
  lineNumbers = true,
}) {
  const classes = useStyles();
  const editorRef = useRef(null);

  function handleEditorMount(editor) {
    editorRef.current = editor;
  }

  function handleCopyAll() {
    const editor = editorRef.current;
    const range = editor.getModel().getFullModelRange();
    editor.setSelection(range);
    editor
      .getAction("editor.action.clipboardCopyWithSyntaxHighlightingAction")
      .run();
  }

  function handleExpandAll() {
    editorRef.current.getAction("editor.unfoldAll").run();
  }

  function handleCollapse() {
    editorRef.current.getAction("editor.foldLevel2").run();
  }

  return (
    <div className={clsx([classes.outerWrapper, className])}>
      <div className={classes.toolbar}>
        <InputLabel variant="outlined" className={classes.label}>
          {label}
        </InputLabel>

        <Tooltip title="Collapse All">
          <IconButton onClick={handleCollapse}>
            <ExpandLessIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Expand All">
          <IconButton onClick={handleExpandAll}>
            <ExpandMoreIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Copy All">
          <IconButton onClick={handleCopyAll}>
            <FileCopyIcon />
          </IconButton>
        </Tooltip>
      </div>
      <div className={classes.editorWrapper}>
        <Editor
          className={classes.monaco}
          height="100%"
          defaultLanguage="json"
          onMount={handleEditorMount}
          defaultValue={JSON.stringify(src, null, 2)}
          options={{
            readOnly: true,
            tabSize: 2,
            minimap: { enabled: false },
            lightbulb: { enabled: false },
            scrollbar: { useShadows: false },
            quickSuggestions: false,
            showFoldingControls: "always",
            lineNumbers: lineNumbers ? "on" : "off",

            // Undocumented see https://github.com/Microsoft/vscode/issues/30795#issuecomment-410998882
            lineDecorationsWidth: 0,
            lineNumbersMinChars: 0,
            renderLineHighlight: "none",

            overviewRulerLanes: 0,
            hideCursorInOverviewRuler: true,
            overviewRulerBorder: false,
          }}
        />
      </div>
    </div>
  );
}

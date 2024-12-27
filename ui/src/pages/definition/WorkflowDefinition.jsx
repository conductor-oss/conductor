import React, { useEffect, useMemo, useReducer, useRef, useState } from "react";
import ReactDOM from "react-dom";
import { useRouteMatch } from "react-router-dom";
import { Button, Text, Select, Pill, LinearProgress } from "../../components";
import { IconButton, MenuItem, Toolbar, Tooltip } from "@material-ui/core";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import _ from "lodash";
import Editor from "@monaco-editor/react";
import {
  useWorkflowDef,
  useWorkflowNamesAndVersions,
} from "../../data/workflow";
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
import ResetConfirmationDialog from "./ResetConfirmationDialog";
import {
  configureMonaco,
  NEW_WORKFLOW_TEMPLATE,
  JSON_FILE_NAME,
} from "../../schema/workflow";
import SaveWorkflowDialog from "./SaveWorkflowDialog";
import update from "immutability-helper";
import { usePushHistory } from "../../components/NavLink";
import { timestampRenderer } from "../../utils/helpers";
import { WorkflowVisualizer } from "orkes-workflow-visualizer";

import {
  KeyboardArrowLeftRounded,
  KeyboardArrowRightRounded,
} from "@material-ui/icons";

const minCodePanelWidth = 500;
const useStyles = makeStyles({
  wrapper: {
    display: "flex",
    height: "100%",
    alignItems: "stretch",
  },
  workflowCodePanel: (workflowDefState) => ({
    width: workflowDefState.toggleGraphPanel
      ? workflowDefState.workflowCodePanelWidth
      : "100%",
    display: "flex",
    flexFlow: "column",
  }),
  workflowGraph: (workflowDefState) => ({
    display: workflowDefState.toggleGraphPanel ? "block" : "none",
    flexGrow: 1,
  }),
  resizer: (workflowDefState) => ({
    display: workflowDefState.toggleGraphPanel ? "block" : "none",
    width: 8,
    cursor: "col-resize",
    backgroundColor: "rgb(45, 45, 45, 0.05)",
    resize: "horizontal",
    "&:hover": {
      backgroundColor: "rgb(45, 45, 45, 0.3)",
    },
  }),
  workflowName: {
    fontWeight: "bold",
  },
  rightButtons: {
    display: "flex",
    flexGrow: 1,
    justifyContent: "flex-end",
    gap: 8,
  },
  editorLineDecorator: {
    backgroundColor: "rgb(45, 45, 45, 0.1)",
  },
});

const actions = {
  NEW_SAVE_COMPLETE: 1,
  SAVE_COMPLETE: 2,
  CONFIRMATION_DIALOG_OPEN: 3,
  CONFIRMATION_DIALOG_CLOSE: 4,
  UPDATE_CODE_PANEL_WIDTH: 5,
  UPDATE_MODIFIED: 6,
  TOGGLE_GRAPH_PANEL: 7,
  SAVE_COMPLETE_CLOSE: 8,
  SAVE_CONFIRMATION_CLOSE: 9,
  SAVE_CONFIRMATION_OPEN: 10,
};

function workflowDefStateReducer(state, action) {
  switch (action.type) {
    case actions.TOGGLE_GRAPH_PANEL:
      return update(state, {
        toggleGraphPanel: {
          $set: !state.toggleGraphPanel,
        },
      });
    case actions.UPDATE_CODE_PANEL_WIDTH:
      return update(state, {
        workflowCodePanelWidth: {
          $set: `${action.newWidth}px`,
        },
      });
    default:
      return state;
  }
}

export default function Workflow() {
  const match = useRouteMatch();
  const navigate = usePushHistory();
  const [saveDialog, setSaveDialog] = useState(null);
  const [resetDialog, setResetDialog] = useState(false); // false=idle, undefined=current_version, otherwise version id
  const [isModified, setIsModified] = useState(false);
  const [dag, setDag] = useState(null);
  const [jsonErrors, setJsonErrors] = useState([]);
  const [decorations, setDecorations] = useState([]);

  const workflowName = _.get(match, "params.name");
  const workflowVersion = _.get(match, "params.version"); // undefined for latest

  const [workflowDefState, dispatch] = useReducer(workflowDefStateReducer, {
    workflowCodePanelWidth: "50%",
    toggleGraphPanel: true,
  });
  const classes = useStyles(workflowDefState);

  const {
    data: workflowDef,
    isFetching,
    refetch: refetchWorkflow,
  } = useWorkflowDef(workflowName, workflowVersion, NEW_WORKFLOW_TEMPLATE);

  const workflowJson = useMemo(
    () => (workflowDef ? JSON.stringify(workflowDef, null, 2) : ""),
    [workflowDef]
  );

  useEffect(() => {
    if (workflowDef) {
      setDag(new WorkflowDAG(null, workflowDef));
    }
  }, [workflowDef]);

  const { data: namesAndVersions, refetch: refetchNamesAndVersions } =
    useWorkflowNamesAndVersions();
  const versions = useMemo(
    () => namesAndVersions.get(workflowName) || [],
    [namesAndVersions, workflowName]
  );

  // Refs
  const editorRef = useRef();
  const resizeRef = useRef();

  // Resize Handle
  const handleMouseDown = () => {
    document.addEventListener("mouseup", handleMouseUp, true);
    document.addEventListener("mousemove", handleMouseMove, true);
  };

  const handleMouseUp = () => {
    document.removeEventListener("mouseup", handleMouseUp, true);
    document.removeEventListener("mousemove", handleMouseMove, true);
  };

  const handleMouseMove = (e) => {
    let boundingClientRect = ReactDOM.findDOMNode(
      resizeRef.current
    ).getBoundingClientRect();
    const newWidth = Math.max(
      minCodePanelWidth,
      e.clientX - boundingClientRect.x
    );
    dispatch({ type: actions.UPDATE_CODE_PANEL_WIDTH, newWidth: newWidth });
  };

  // Version Change or Reset
  const handleResetVersion = (version) => {
    if (isModified) {
      setResetDialog(version);
    } else {
      changeVersionOrReset(version);
    }
  };

  const changeVersionOrReset = (version) => {
    if (version === workflowVersion) {
      // Reset to fetched version
      editorRef.current.getModel().setValue(workflowJson);
    } else if (_.isUndefined(version)) {
      navigate(`/workflowDef/${workflowName}`);
    } else {
      navigate(`/workflowDef/${workflowName}/${version}`);
    }

    setResetDialog(false);
    setIsModified(false);
  };

  // Saving
  const handleOpenSave = () => {
    const modified = editorRef.current.getValue();

    setSaveDialog({
      original: workflowName ? workflowJson : "",
      originalObj: workflowName ? workflowDef : null,
      modified: modified,
    });
  };

  const handleSaveCancel = () => {
    setSaveDialog(null);
  };

  const handleSaveSuccess = (name, version) => {
    setSaveDialog(null);
    setIsModified(false);
    refetchNamesAndVersions();

    if (name === workflowName && version === workflowVersion) {
      refetchWorkflow();
    } else {
      navigate(`/workflowDef/${name}/${version}`);
    }
  };

  // Monaco Handlers
  const handleEditorWillMount = (monaco) => {
    configureMonaco(monaco);
  };

  const handleEditorDidMount = (editor) => {
    editorRef.current = editor;
  };

  const handleValidate = (markers) => {
    setJsonErrors(markers);
  };

  const handleChange = (v) => {
    setIsModified(v !== workflowJson);
  };

  const handleWorkflowNodeClick = (node) => {
    let editor = editorRef.current.getModel();
    let searchResult = editor.findMatches(`"taskReferenceName": "${node.ref}"`);
    if (searchResult.length) {
      editorRef.current.revealLineInCenter(
        searchResult[0]?.range?.startLineNumber,
        0
      );
      setDecorations(
        editorRef.current.deltaDecorations(decorations, [
          {
            range: searchResult[0]?.range,
            options: {
              isWholeLine: true,
              inlineClassName: classes.editorLineDecorator,
            },
          },
        ])
      );
    }
  };

  return (
    <>
      <Helmet>
        <title>
          Conductor UI - Workflow Definition - {workflowName || "New Workflow"}
        </title>
      </Helmet>

      <ResetConfirmationDialog
        version={resetDialog}
        onConfirm={changeVersionOrReset}
        onClose={() => setResetDialog(false)}
      />

      <SaveWorkflowDialog
        document={saveDialog}
        onCancel={handleSaveCancel}
        onSuccess={handleSaveSuccess}
      />

      {isFetching && <LinearProgress />}
      <div className={classes.wrapper}>
        <div className={classes.workflowCodePanel} ref={resizeRef}>
          <Toolbar>
            <Text className={classes.workflowName}>
              {workflowName || "NEW"}
            </Text>

            <Select
              disabled={!workflowDef}
              value={_.isUndefined(workflowVersion) ? "" : workflowVersion}
              displayEmpty
              renderValue={(v) =>
                v === "" ? "Latest Version" : `Version ${v}`
              }
              onChange={(evt) => handleResetVersion(evt.target.value)}
            >
              <MenuItem value="">Latest Version</MenuItem>
              {versions.map((row) => (
                <MenuItem value={row.version} key={row.version}>
                  Version {row.version} ({versionTime(row)})
                </MenuItem>
              ))}
            </Select>

            {isModified ? (
              <Pill color="yellow" label="Modified" />
            ) : (
              <Pill label="Unmodified" />
            )}
            {!_.isEmpty(jsonErrors) && (
              <Tooltip
                disableFocusListener
                title="There are validation or syntax errors. Validation errors at the root level may be seen by hovering over the opening brace."
              >
                <div>
                  <Pill color="red" label="Validation" />
                </div>
              </Tooltip>
            )}

            <div className={classes.rightButtons}>
              <Button
                disabled={!_.isEmpty(jsonErrors) || !isModified}
                onClick={handleOpenSave}
              >
                Save
              </Button>
              <Button
                disabled={!isModified}
                onClick={() => handleResetVersion(workflowVersion)}
                variant="secondary"
              >
                Reset
              </Button>

              <IconButton
                onClick={() => dispatch({ type: actions.TOGGLE_GRAPH_PANEL })}
              >
                {workflowDefState.toggleGraphPanel && (
                  <KeyboardArrowRightRounded />
                )}
                {!workflowDefState.toggleGraphPanel && (
                  <KeyboardArrowLeftRounded />
                )}
              </IconButton>
            </div>
          </Toolbar>
          <Editor
            height="100%"
            width="100%"
            theme="vs-light"
            language="json"
            value={workflowJson}
            autoIndent={true}
            beforeMount={handleEditorWillMount}
            onMount={handleEditorDidMount}
            onValidate={handleValidate}
            onChange={handleChange}
            options={{
              smoothScrolling: true,
              selectOnLineNumbers: true,
              minimap: {
                enabled: false,
              },
            }}
            path={JSON_FILE_NAME}
          />
        </div>
        <span
          className={classes.resizer}
          onMouseDown={(e) => handleMouseDown(e)}
        />
        <div className={classes.workflowGraph} style={{ overflow: "scroll" }}>
          {dag && dag?.workflowDef && (
            <WorkflowVisualizer
              maxHeightOverride
              maxWidthOverride
              pannable
              zoomable
              zoom={0.7}
              data={dag?.workflowDef}
              onClick={(e, data) => handleWorkflowNodeClick({ ref: data?.id })}
            />
          )}
        </div>
      </div>
    </>
  );
}

function versionTime(versionObj) {
  return (
    versionObj &&
    timestampRenderer(versionObj.updateTime || versionObj.createTime)
  );
}

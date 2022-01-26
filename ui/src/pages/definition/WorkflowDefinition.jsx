import React, { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import ReactDOM from "react-dom";
import { useRouteMatch } from "react-router-dom";
import { Box, IconButton, MenuItem, Popover, Typography } from "@material-ui/core";
import sharedStyles from "../styles";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import { usePushHistory } from "../../components/NavLink";
import _ from "lodash";
import { LinearProgress, Select, Text } from "../../components";
import { BootstrapActionButton } from "../../components/CustomButtons";
import Editor, { DiffEditor } from "@monaco-editor/react";
import { useAction, useFetch, useWorkflowNamesAndVersions } from "../../utils/query";
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
import WorkflowGraph from "../../components/diagram/WorkflowGraph";
import ConfirmChoiceDialog from "../../components/ConfirmChoiceDialog";
import { configureMonaco, JSON_FILE_NAME } from "../../codegen/CodeEditorUtils";
import { NEW_WORKFLOW_TEMPLATE } from "../../codegen/JSONSchemaWorkflow";
import update from "immutability-helper";
import {
  KeyboardArrowLeftRounded,
  KeyboardArrowRightRounded
} from "@material-ui/icons";

const useStyles = makeStyles(sharedStyles);
const minCodePanelWidth = 150;
const minEditor_Width = 590;

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
    case actions.UPDATE_MODIFIED:
      return update(state, {
        workflowIsModified: {
          $set: action.workflowIsModified,
        },
        modifiedWorkflow: {
          $set: action.modifiedWorkflow,
        },
      });
    case actions.UPDATE_CODE_PANEL_WIDTH:
      return update(state, {
        workflowCodePanelWidth: {
          $set: `${action.newWidth}px`,
        },
      });
    case actions.CONFIRMATION_DIALOG_CLOSE:
      return update(state, {
        confirmationDialogOpen: {
          $set: false,
        },
      });
    case actions.CONFIRMATION_DIALOG_OPEN:
      return update(state, {
        confirmationDialogOpen: {
          $set: true,
        },
      });
    case actions.SAVE_CONFIRMATION_CLOSE:
      return update(state, {
        saveConfirmationOpen: {
          $set: false,
        },
      });
    case actions.SAVE_CONFIRMATION_OPEN:
      return update(state, {
        saveConfirmationOpen: {
          $set: true,
        },
      });
    case actions.SAVE_COMPLETE:
      return update(state, {
        saveComplete: {
          $set: true,
        },
        saveConfirmationOpen: {
          $set: false,
        },
        popoverMessage: {
          $set: action.popoverMessage,
        },
      });
    case actions.SAVE_COMPLETE_CLOSE:
      return update(state, {
        saveComplete: {
          $set: false,
        },
      });
    case actions.NEW_SAVE_COMPLETE:
      return update(state, {
        isNewWorkflow: {
          $set: false,
        },
        workflowName: {
          $set: action.modifiedName,
        },
        modifiedWorkflow: {
          $set: "",
        },
        workflowIsModified: {
          $set: false,
        },
        saveComplete: {
          $set: true,
        },
        saveConfirmationOpen: {
          $set: false,
        },
        popoverMessage: {
          $set: action.popoverMessage,
        },
      });
    default:
      return state;
  }
}

const WORKFLOW_SAVED_SUCCESSFULLY = "Workflow saved successfully.";
const WORKFLOW_SAVE_FAILED = "Failed to save the workflow definition.";

export default function Workflow() {
  const classes = useStyles();
  const match = useRouteMatch();
  let isNewWorkflow = (match.url === "/newWorkflowDef");


  const [workflowDefState, dispatch] = useReducer(workflowDefStateReducer, {
    workflowName: isNewWorkflow ? "NEW" : _.get(match, "params.name"),
    isNewWorkflow: isNewWorkflow,
    modifiedWorkflow: "",
    workflowIsModified: false,
    saveComplete: false,
    workflowCodePanelWidth: '50%',
    toggleGraphPanel: true,
    confirmationDialogOpen: false,
    saveConfirmationOpen: false,
    popoverMessage: ""
  });

  const version = _.get(match, "params.version");
  const pushHistory = usePushHistory();

  let path = `/metadata/workflow/${workflowDefState.workflowName}`;
  if (version) path += `?version=${version}`;

  const setVersion = (newVersion) => {
    const versionStr = newVersion === "" ? "" : `/${newVersion}`;
    pushHistory(`/workflowDef/${workflowDefState.workflowName}${versionStr}`);
  };

  const {
    data: workflow,
    isFetching,
  } = useFetch(path, { enabled: !workflowDefState.isNewWorkflow });

  const dag = useMemo(
    () => workflowDefState.isNewWorkflow ? (new WorkflowDAG(null, NEW_WORKFLOW_TEMPLATE)) : (workflow && new WorkflowDAG(null, workflow)),
    [workflow, workflowDefState.isNewWorkflow]
  );

  const namesAndVersions = useWorkflowNamesAndVersions();
  let versions = namesAndVersions.get(workflowDefState.workflowName) || [];
  let themes = [{ name: "Light Theme", value: "vs-light" }, { name: "Dark Theme", value: "vs-dark" }];

  const resizableBox = useRef(null);

  const saveButtonRef = useRef(null);

  const handleMouseDown = () => {
    document.addEventListener("mouseup", handleMouseUp, true);
    document.addEventListener("mousemove", handleMouseMove, true);
  };

  function dispatchSaveComplete(popoverMessage = WORKFLOW_SAVED_SUCCESSFULLY) {
    dispatch({ type: actions.SAVE_COMPLETE, popoverMessage: popoverMessage });
    setTimeout(() => dispatch({ type: actions.SAVE_COMPLETE_CLOSE }), 1200);
  }

  const saveWorkflowDefAction = useAction("/metadata/workflow", "put", {
    onSuccess(data) {
      if(data && data.status && (data.status !== 200 || data.status !== 201)) {
          dispatchSaveComplete(`${WORKFLOW_SAVE_FAILED} - Error: ${data.status} ${data.error}`);
          return console.error(data);
      }
      let modifiedName = parseAndExtractModifiedName();
      dispatch({ type: actions.NEW_SAVE_COMPLETE, modifiedName: modifiedName, popoverMessage: WORKFLOW_SAVED_SUCCESSFULLY});
      setTimeout(() => dispatch({ type: actions.SAVE_COMPLETE_CLOSE }), 1200);
      pushHistory(`/workflowDef/${modifiedName}`);
      return console.log("onsuccess", data);
    },
    onError: (err) => {
      dispatchSaveComplete(`${WORKFLOW_SAVE_FAILED} - Error: ${err}`);
      return console.log("onerror", err);
    }
  });

  const isLoading = saveWorkflowDefAction.isLoading;

  const saveWorkflowDefJson = function() {
    if (workflowDefState.workflowIsModified) {
      let postBody = `[${workflowDefState.modifiedWorkflow}]`; // API expects an array
      // noinspection JSCheckFunctionSignatures
      saveWorkflowDefAction.mutate({
        body: postBody
      });
    }
  };

  const handleResetConfirmation = (val) => {
    dispatch({ type: actions.CONFIRMATION_DIALOG_CLOSE});
    if (val) {
      let convertedString = convertJSONToString();
      monacoObjects.current.setValue(convertedString);
      diffMonacoObjects.current.getModel().modified.setValue(convertedString);
      dispatch({ type: actions.UPDATE_MODIFIED, workflowIsModified: false, modifiedWorkflow: convertedString,});
    }
  };

  const handleMouseUp = () => {
    document.removeEventListener("mouseup", handleMouseUp, true);
    document.removeEventListener("mousemove", handleMouseMove, true);
  };

  const handleMouseMove = useCallback(e => {
    let boundingClientRect = ReactDOM.findDOMNode(resizableBox.current).getBoundingClientRect();
    const newWidth = Math.max(minCodePanelWidth, e.clientX - boundingClientRect.x);
    dispatch({ type: actions.UPDATE_CODE_PANEL_WIDTH, newWidth: newWidth});
  }, []);

  let convertJSONToString = () => {
    if (isNewWorkflow) {
      return JSON.stringify(NEW_WORKFLOW_TEMPLATE, null, 2);
    }
    if (workflow) {
      return JSON.stringify(workflow, null, 2);
    }
    return "";
  };

  const convertedJSONString = convertJSONToString();

  const handleChange = (d) => {
    if(convertedJSONString !== monacoObjects.current.getValue()) {
      dispatch({ type: actions.UPDATE_MODIFIED, workflowIsModified: true, modifiedWorkflow: monacoObjects.current.getValue(),});
    }
  };

  const monacoObjects = useRef(null);
  const diffMonacoObjects = useRef(null);
  const editorDidMount = (editor) => {
    monacoObjects.current = editor;
    monacoObjects.current.onDidChangeModelContent(handleChange);
  };
  const diffEditorDidMount = (editor) => {
    diffMonacoObjects.current = editor;
  };

  useEffect(() => {
    if ((workflowDefState.isNewWorkflow || workflow) && monacoObjects.current) {
      monacoObjects.current.setValue(convertedJSONString);
      diffMonacoObjects.current.getModel().modified.setValue(convertedJSONString);
    }
  }, [workflow, convertedJSONString, workflowDefState.isNewWorkflow]);

  const handleWorkflowCodeSave = (e) => {
    dispatch({ type: actions.SAVE_CONFIRMATION_OPEN});
  };

  const [editorState, setEditorState] = useState({
    editorTheme: "vs-light",
    editorOptions: {
      selectOnLineNumbers: true
    }
  });

  function handleEditorWillMount(monaco) {
    configureMonaco(monaco);
  }

  function parseAndExtractModifiedName() {
    const modifiedWorkflowJson = JSON.parse(workflowDefState.modifiedWorkflow);
    return modifiedWorkflowJson["name"];
  }

  const handleClosePopover = () => {
    dispatch({ type: actions.SAVE_COMPLETE_CLOSE });
  };

  const handleWorkflowCodeSaveConfirm = () => {
    if (workflowDefState.isNewWorkflow) {
      if (workflowDefState.workflowIsModified) {
        try {
          let newName = parseAndExtractModifiedName();
          if (!newName || newName === "" || !(/^\w+$/.test(newName))) {
            alert("A valid unique name is required for the workflow");
            return;
          }
        } catch (e) {
          alert("Unable to parse changed workflow - please check if the workflow code is a valid JSON " + e);
          return;
        }
      }
    }
    saveWorkflowDefJson();
  };

  const handleWorkflowCodeReset = () => {
    if (workflowDefState.workflowIsModified) {
      dispatch({ type: actions.CONFIRMATION_DIALOG_OPEN});
    } else {
      console.log("No changes detected in the editor");
    }
  };

  function setTheme(value) {
    setEditorState(prevState => {
      return {
        ...prevState,
        editorTheme: value
      };
    });
  }

  return (
    <div className={classes.wrapper}>

      <Helmet>
        <title>Conductor UI - Workflow Definition - {workflowDefState.workflowName}</title>
      </Helmet>
      {workflowDefState.confirmationDialogOpen && (
        <ConfirmChoiceDialog handleConfirmationValue={(val) => handleResetConfirmation(val)}
                             message={"You will lose all changes made in the editor. Please confirm resetting workflow to its original state."} />
      )}
      <Box className={classes.definitionEditorParentBox}>
        <Box className={classes.definitionEditorBox}>
          <Box className={classes.definitionEditorBoxPanel}>
            <Box style={{
              minWidth: workflowDefState.toggleGraphPanel ? 300 : "100%",
              flex: "0 0 auto",
              position: "relative",
              width: workflowDefState.workflowCodePanelWidth
            }} ref={resizableBox}>
              <Box style={{
                display: "flex",
                flexFlow: "column",
                height: "100%",
                overflowX: "auto"
              }}>
                {(isFetching || isLoading) && <LinearProgress />}
                <Box id={"menuBar"} style={{
                  minWidth: minEditor_Width,
                  flex: "0 1 auto",
                  display: "flex",
                  flexFlow: "row",
                  flexWrap: "wrap"
                }}>
                  <Box id="secondRowMenu" className={classes.definitionEditorSecondRowMenu}
                       style={{ minWidth: minEditor_Width }}>
                    <Text style={{ marginLeft: 8 }}>
                      {workflowDefState.workflowName}
                    </Text>
                    <Select
                      value={_.isUndefined(version) ? "" : version}
                      displayEmpty
                      renderValue={(v) => (v === "" ? "Latest Version" : v)}
                      style={{ height: 30, marginLeft: 8, marginTop: 0 }}
                      onChange={(evt) => setVersion(evt.target.value)}>
                      <MenuItem value="">Latest Version</MenuItem>
                      {versions.map((ver) => (
                        <MenuItem value={ver} key={ver}>
                          Version {ver}
                        </MenuItem>
                      ))}
                    </Select>
                    <Box style={{
                      flexGrow: 2,
                      display: "flex",
                      height: 30,
                      justifyContent: "flex-start",
                      paddingTop: 2,
                      alignItems: "center"
                    }}>
                      {workflowDefState.saveConfirmationOpen &&
                      <>
                        <BootstrapActionButton size="small" color={"inherit"}
                                               style={{ width: 120, fontSize: 12, lineHeight: 1.4, marginLeft: 10 }}
                                               onClick={() => handleWorkflowCodeSaveConfirm()}>
                          Confirm Save
                        </BootstrapActionButton>
                        <BootstrapActionButton size="small" color={"inherit"}
                                               style={{ width: 50, fontSize: 12, lineHeight: 1.4, marginLeft: 10 }}
                                               onClick={() => dispatch({ type: actions.SAVE_CONFIRMATION_CLOSE})}>
                          Cancel
                        </BootstrapActionButton>
                      </>
                      }
                      {!workflowDefState.saveConfirmationOpen &&
                      <>
                        <BootstrapActionButton ref={saveButtonRef} size="small" color={"inherit"}
                                               style={{ width: 50, fontSize: 12, lineHeight: 1.4, marginLeft: 10 }}
                                               onClick={(e) => handleWorkflowCodeSave(e)}>
                          Save
                        </BootstrapActionButton>
                        <Popover
                          open={workflowDefState.saveComplete}
                          anchorEl={saveButtonRef.current}
                          onClose={handleClosePopover}
                          anchorOrigin={{
                            vertical: 'bottom',
                            horizontal: 'left',
                          }}
                          className={classes.popover}
                        >
                          <Typography>{workflowDefState.popoverMessage}</Typography>
                        </Popover>
                        <BootstrapActionButton size="small" color={"inherit"}
                                               style={{ width: 50, fontSize: 12, lineHeight: 1.4, marginLeft: 10 }}
                                               onClick={() => handleWorkflowCodeReset()}>
                          Reset
                        </BootstrapActionButton>
                      </>
                      }
                    </Box>
                    <Select
                      value={editorState.editorTheme}
                      displayEmpty
                      renderValue={v => themes.filter(v => v.value === editorState.editorTheme)[0].name}
                      style={{ height: 30, marginRight: 10, marginTop: 0 }}
                      onChange={(evt) => setTheme(evt.target.value)}>
                      {themes.map((theme) => (
                        <MenuItem key={theme.name} value={theme.value}>
                          {theme.name}
                        </MenuItem>
                      ))}
                    </Select>
                    <IconButton className={classes.iconButton} size="small"
                                onClick={() => dispatch({ type: actions.TOGGLE_GRAPH_PANEL })}>
                      {workflowDefState.toggleGraphPanel && <KeyboardArrowRightRounded />}
                      {!workflowDefState.toggleGraphPanel && <KeyboardArrowLeftRounded />}
                    </IconButton>
                  </Box>
                </Box>
                <Box id={"editorContent"} style={{
                  flex: "1 1 auto",
                  minWidth: !workflowDefState.saveConfirmationOpen ? minEditor_Width : "0px",
                  overflow: "hidden",
                  maxHeight: !workflowDefState.saveConfirmationOpen ? "100%" : "0px",
                  visibility: !workflowDefState.saveConfirmationOpen ? "visible" : "hidden",
                  maxWidth: !workflowDefState.saveConfirmationOpen ? "100%" : "0px",
                  width: !workflowDefState.saveConfirmationOpen ? "100%" : "0px"
                }}>
                  <Editor
                    height={"100%"}
                    width={"100%"}
                    theme={editorState.editorTheme}
                    // language="typescript"
                    language="json"
                    // value={"// Typescript - Expand the WorkflowBuilder to compose your workflow. }" +
                    value={convertedJSONString}
                    autoIndent={true}
                    beforeMount={handleEditorWillMount}
                    onMount={editorDidMount}
                    options={editorState.editorOptions}
                    // onChange={handleChange}
                    // path={"file:///main.tsx"}
                    path={JSON_FILE_NAME}
                  />
                </Box>
                <Box id={"editorContent"} style={{
                  flex: "1 1 auto",
                  minWidth: workflowDefState.saveConfirmationOpen ? minEditor_Width : "0px",
                  maxHeight: workflowDefState.saveConfirmationOpen ? "100%" : "0px",
                  overflow: "hidden",
                  visibility: workflowDefState.saveConfirmationOpen ? "visible" : "hidden",
                  maxWidth: workflowDefState.saveConfirmationOpen ? "100%" : "0px",
                  width: workflowDefState.saveConfirmationOpen ? "100%" : "0px"
                }}>
                  <DiffEditor
                    height={"100%"}
                    width={"100%"}
                    theme={editorState.editorTheme}
                    language="json"
                    original={workflowDefState.isNewWorkflow ? "" : convertedJSONString}
                    modified={workflowDefState.modifiedWorkflow}
                    autoIndent={true}
                    onMount={diffEditorDidMount}
                    options={editorState.editorOptions}
                  />
                </Box>
              </Box>
            </Box>
            <span className={classes.resizer} onMouseDown={e => handleMouseDown(e)} />
            <Box style={{
              visibility: workflowDefState.toggleGraphPanel ? "visible" : "hidden",
              maxWidth: workflowDefState.toggleGraphPanel ? "100%" : "0px",
              width: workflowDefState.toggleGraphPanel ? "100%" : "0px",
              overflow: "hidden",
              zIndex: 0,
              flex: "1 1 0%",
              position: "relative"
            }}>
              {dag && <WorkflowGraph dag={dag} />}
            </Box>
          </Box>
        </Box>
      </Box>
    </div>
  );
}

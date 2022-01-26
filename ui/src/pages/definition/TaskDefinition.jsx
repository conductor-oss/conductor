import React, { useEffect, useRef, useState } from "react";
import { useRouteMatch } from "react-router-dom";
import { Box, MenuItem, Popover, Typography } from "@material-ui/core";
import sharedStyles from "../styles";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import _ from "lodash";
import { LinearProgress, Select, Text } from "../../components";
import { BootstrapActionButton } from "../../components/CustomButtons";
import Editor, { DiffEditor } from "@monaco-editor/react";
import { useAction, useFetch } from "../../utils/query";
import ConfirmChoiceDialog from "../../components/ConfirmChoiceDialog";
import { configureMonaco, JSON_FILE_TASK_NAME } from "../../codegen/CodeEditorUtils";
import { usePushHistory } from "../../components/NavLink";
import { NEW_TASK_TEMPLATE } from "../../codegen/JSONSchemaWorkflow";

const useStyles = makeStyles(sharedStyles);
const minEditor_Width = 590;

const taskDefinitionSavedSuccessfully = "Task definition saved successfully.";
const taskDefinitionSaveFailed = "Failed to save task definition.";

export default function TaskDefinition() {
  const classes = useStyles();
  const match = useRouteMatch();
  let isNewTaskDef = (match.url === "/newTaskDef");

  const [taskDefState, setTaskDefState] = useState({
    taskName: isNewTaskDef ? "NEW" : _.get(match, "params.name"),
    isNewTaskDef: isNewTaskDef,
    modifiedTaskDefinition: "",
    taskIsModified: false,
    saveComplete: false,
    popoverMessage: taskDefinitionSavedSuccessfully,
    saveConfirmationOpen: false,
  });

  let path = `/metadata/taskdefs/${taskDefState.taskName}`;
  const pushHistory = usePushHistory();

  const {
    data: taskdefinition,
    isFetching
  } = useFetch(path, { enabled: !taskDefState.isNewTaskDef });


  const themes = [{ name: "Light Theme", value: "vs-light" }, { name: "Dark Theme", value: "vs-dark" }];
  const resizableBox = useRef(null);
  const saveButtonRef = useRef(null);
  const [confirmationDialogOpen, setConfirmationDialogOpen] = useState(false);

  function parseAndExtractModifiedName() {
    const modifiedTaskJson = JSON.parse(taskDefState.modifiedTaskDefinition);
    return modifiedTaskJson["name"];
  }

  const handleClosePopover = () => {
    setTaskDefState(prevState => {
      return {
        ...prevState,
        saveComplete: false
      };
    });
  };

  function handleOnSuccess(data) {
    if(data && data.status && (data.status !== 200 || data.status !== 201)) {
      setTaskDefState(prevState => {
        return {
          ...prevState,
          saveComplete: true,
          saveConfirmationOpen: false,
          popoverMessage: `${taskDefinitionSaveFailed} - Error: ${data.status} - ${data.error || data.message}`,
        }
      });
      return console.error(data);
    }
    let modifiedName = parseAndExtractModifiedName();
    setTaskDefState({
      isNewTaskDef: false,
      taskName: modifiedName,
      modifiedTaskDefinition: "",
      taskIsModified: false,
      saveComplete: true,
      popoverMessage: taskDefinitionSavedSuccessfully,
      saveConfirmationOpen: false,
    });
    pushHistory(`/taskDef/${modifiedName}`);
    return console.log("onsuccess", data);
  }

  const saveTaskDefAction = useAction("/metadata/taskdefs", "put", {
    onSuccess: (data) => handleOnSuccess(data),
    onError: (err) => console.log("onerror", err)
  });

  const saveNewTaskDefAction = useAction("/metadata/taskdefs", "post", {
    onSuccess: (data) => handleOnSuccess(data),
    onError: (err) => console.log("onerror", err)
  });

  const isLoading = saveTaskDefAction.isLoading || saveNewTaskDefAction.isLoading;

  const saveTaskDefJson = function() {
    if (taskDefState.taskIsModified) {
      let postBody = `${taskDefState.modifiedTaskDefinition}`;
      if (taskDefState.isNewTaskDef) {
        // noinspection JSCheckFunctionSignatures
        saveNewTaskDefAction.mutate({
          body: `[${postBody}]` // needs an array
        });
      } else {
        // noinspection JSCheckFunctionSignatures
        saveTaskDefAction.mutate({
          body: postBody
        });
      }
    }
  };

  const handleResetConfirmation = (val) => {
    setConfirmationDialogOpen(false);
    if (val) {
      let convertedString = convertJSONToString();
      monacoObjects.current.setValue(convertedString);
      diffMonacoObjects.current.getModel().modified.setValue(convertedString);
      setTaskDefState(prevState => {
        return {
          ...prevState,
          taskIsModified: false,
          modifiedTaskDefinition: convertedString
        };
      });
    }
  };

  const handleChange = (d) => {
    setTaskDefState(prevState => {
      return {
        ...prevState,
        taskIsModified: true,
        modifiedTaskDefinition: monacoObjects.current.getValue()
      };
    });
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

  let convertJSONToString = () => {
    if (taskDefState.isNewTaskDef) {
      return JSON.stringify(NEW_TASK_TEMPLATE, null, 2);
    }
    if (taskdefinition) {
      return JSON.stringify(taskdefinition, null, 2);
    }
    return "";
  };

  const convertedJSONString = convertJSONToString();

  useEffect(() => {
    if ((taskDefState.isNewTaskDef || taskdefinition) && monacoObjects.current) {
      monacoObjects.current.setValue(convertedJSONString);
      diffMonacoObjects.current.getModel().modified.setValue(convertedJSONString);
    }
  }, [taskdefinition, convertedJSONString, taskDefState.isNewTaskDef]);

  const handleTaskCodeSave = () => {
    setSaveConfirmationOpen(true);
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

  const handleTaskCodeSaveConfirm = () => {
    if (taskDefState.isNewTaskDef) {
      if (taskDefState.taskIsModified) {
        try {
          let newName = parseAndExtractModifiedName();
          if (!newName || newName === "" || !(/^\w+$/.test(newName))) {
            alert("A valid unique name is required for the task");
            return;
          }
        } catch (e) {
          alert("Unable to parse changed task - please check if the task code is a valid JSON " + e);
          return;
        }
      }
    }
    saveTaskDefJson();
  };

  const handleTaskCodeReset = () => {
    if (taskDefState.taskIsModified) {
      setConfirmationDialogOpen(true);
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

  function setSaveConfirmationOpen(status) {
    return setTaskDefState(prevState => {
      return {
        ...prevState,
        saveConfirmationOpen: status
      }
    });
  }

  return (
    <div className={classes.wrapper}>
      <Helmet>
        <title>Conductor UI - Task Definition - {taskDefState.taskName}</title>
      </Helmet>
      {confirmationDialogOpen && (
        <ConfirmChoiceDialog handleConfirmationValue={(val) => handleResetConfirmation(val)}
                             message={"You will lose all changes made in the editor. Please confirm resetting task definition to its original state."} />
      )}
      <Box className={classes.definitionEditorParentBox}>
        <Box className={classes.definitionEditorBox}>
          <Box className={classes.definitionEditorBoxPanel}>
            <Box style={{
              minWidth: "100%",
              flex: "0 0 auto",
              position: "relative",
              width: "100%"
            }} ref={resizableBox}>
              <Box style={{
                display: "flex",
                flexFlow: "column",
                height: "100%",
                overflowX: "auto"
              }}>
                {(isFetching || isLoading) && <LinearProgress />}
                <Box id={"menuBar"} style={{
                  minWidth: "100%",
                  flex: "0 1 auto",
                  display: "flex",
                  flexFlow: "row",
                  flexWrap: "wrap"
                }}>
                  <Box id="secondRowMenu" className={classes.definitionEditorSecondRowMenu}
                       style={{ minWidth: minEditor_Width }}>
                    <Text style={{ marginLeft: 8 }}>
                      Task Definition: <b>{taskDefState.taskName}</b>
                    </Text>
                    <Box style={{
                      flexGrow: 2,
                      display: "flex",
                      height: 30,
                      justifyContent: "flex-start",
                      paddingTop: 2,
                      alignItems: "center"
                    }}>
                      {taskDefState.saveConfirmationOpen &&
                      <>
                        <BootstrapActionButton size="small" color={"inherit"}
                                               style={{ width: 120, fontSize: 12, lineHeight: 1.4, marginLeft: 10 }}
                                               onClick={() => handleTaskCodeSaveConfirm()}>
                          Confirm Save
                        </BootstrapActionButton>
                        <BootstrapActionButton size="small" color={"inherit"}
                                               style={{ width: 50, fontSize: 12, lineHeight: 1.4, marginLeft: 10 }}
                                               onClick={() => setSaveConfirmationOpen(false)}>
                          Cancel
                        </BootstrapActionButton>
                      </>
                      }
                      {!taskDefState.saveConfirmationOpen &&
                      <>
                        <BootstrapActionButton ref={saveButtonRef} size="small" color={"inherit"}
                                               style={{ width: 50, fontSize: 12, lineHeight: 1.4, marginLeft: 10 }}
                                               onClick={() => handleTaskCodeSave()}>
                          Save
                        </BootstrapActionButton>
                        {saveButtonRef.current && <Popover
                          open={taskDefState.saveComplete}
                          anchorEl={saveButtonRef.current}
                          onClose={handleClosePopover}
                          anchorOrigin={{
                            vertical: "bottom",
                            horizontal: "left"
                          }}
                          className={classes.popover}
                        >
                          <Typography>{taskDefState.popoverMessage}</Typography>
                        </Popover>}
                        <BootstrapActionButton size="small" color={"inherit"}
                                               style={{ width: 50, fontSize: 12, lineHeight: 1.4, marginLeft: 10 }}
                                               onClick={() => handleTaskCodeReset()}>
                          Reset
                        </BootstrapActionButton>
                      </>
                      }
                      <Text style={{ marginLeft: 8 }}>
                        <span style={{ fontSize: 11, fontStyle: "italic" }}>(Task definitions are cached for up to 2 mins. You might see stale values if you just updated this task)</span>
                      </Text>
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
                  </Box>
                </Box>
                <Box id={"editorContent"} style={{
                  flex: "1 1 auto",
                  minWidth: !taskDefState.saveConfirmationOpen ? minEditor_Width : "0px",
                  overflow: "hidden",
                  maxHeight: !taskDefState.saveConfirmationOpen ? "100%" : "0px",
                  visibility: !taskDefState.saveConfirmationOpen ? "visible" : "hidden",
                  maxWidth: !taskDefState.saveConfirmationOpen ? "100%" : "0px",
                  width: !taskDefState.saveConfirmationOpen ? "100%" : "0px"
                }}>
                  <Editor
                    height={"100%"}
                    width={"100%"}
                    theme={editorState.editorTheme}
                    language="json"
                    value={convertedJSONString}
                    autoIndent={true}
                    beforeMount={handleEditorWillMount}
                    onMount={editorDidMount}
                    options={editorState.editorOptions}
                    // onChange={handleChange}
                    path={JSON_FILE_TASK_NAME}
                  />
                </Box>
                <Box id={"editorContent"} style={{
                  flex: "1 1 auto",
                  minWidth: taskDefState.saveConfirmationOpen ? minEditor_Width : "0px",
                  maxHeight: taskDefState.saveConfirmationOpen ? "100%" : "0px",
                  overflow: "hidden",
                  visibility: taskDefState.saveConfirmationOpen ? "visible" : "hidden",
                  maxWidth: taskDefState.saveConfirmationOpen ? "100%" : "0px",
                  width: taskDefState.saveConfirmationOpen ? "100%" : "0px"
                }}>
                  <DiffEditor
                    height={"100%"}
                    width={"100%"}
                    theme={editorState.editorTheme}
                    language="json"
                    original={taskDefState.isNewTaskDef ? "" : convertedJSONString}
                    modified={taskDefState.modifiedTaskDefinition}
                    autoIndent={true}
                    onMount={diffEditorDidMount}
                    options={editorState.editorOptions}
                  />
                </Box>
              </Box>
            </Box>
          </Box>
        </Box>
      </Box>
    </div>
  );
}

import React, { useMemo, useRef, useState } from "react";
import { useRouteMatch } from "react-router-dom";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import { Button, LinearProgress, Pill, Text } from "../../components";
import _ from "lodash";
import Editor from "@monaco-editor/react";
import { Toolbar } from "@material-ui/core";
import {
  configureMonaco,
  NEW_SCHEDULER_TEMPLATE,
} from "../../schema/scheduler";
import { useSchedulerDef } from "../../data/scheduler";
import ResetConfirmationDialog from "./ResetConfirmationDialog";
import { usePushHistory } from "../../components/NavLink";
import SaveSchedulerDialog from "./SaveSchedulerDialog";
import SchedulerDisabledBanner, {
  isSchedulerDisabled,
} from "../../components/SchedulerDisabledBanner";

const useStyles = makeStyles({
  wrapper: {
    display: "flex",
    height: "100%",
    alignItems: "stretch",
    flexDirection: "column",
  },
  name: {
    fontWeight: "bold",
  },
  rightButtons: {
    display: "flex",
    flexGrow: 1,
    justifyContent: "flex-end",
    gap: 8,
  },
});

export default function SchedulerDefinition() {
  const classes = useStyles();
  const match = useRouteMatch();
  const navigate = usePushHistory();

  const [isModified, setIsModified] = useState(false);
  const [jsonErrors, setJsonErrors] = useState([]);
  const [resetDialog, setResetDialog] = useState(false);
  const [saveDialog, setSaveDialog] = useState(null);

  const editorRef = useRef();
  const schedulerName = _.get(match, "params.name");

  const {
    data: schedulerDef,
    isFetching,
    error,
    refetch,
  } = useSchedulerDef(schedulerName, NEW_SCHEDULER_TEMPLATE);

  const schedulerJson = useMemo(
    () => (schedulerDef ? JSON.stringify(schedulerDef, null, 2) : ""),
    [schedulerDef]
  );

  const handleOpenSave = () => {
    setSaveDialog({
      original: schedulerName ? schedulerJson : "",
      originalObj: schedulerName ? schedulerDef : null,
      modified: editorRef.current.getModel().getValue(),
    });
  };

  const handleSaveSuccess = (name) => {
    setSaveDialog(null);
    setIsModified(false);

    if (name === schedulerName) {
      refetch();
    } else {
      navigate(`/schedulerDef/${name}`);
    }
  };

  const doReset = () => {
    editorRef.current.getModel().setValue(schedulerJson);
    setResetDialog(false);
    setIsModified(false);
  };

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
    setIsModified(v !== schedulerJson);
  };

  return (
    <>
      <Helmet>
        <title>
          Conductor UI - Schedule Definition -{" "}
          {schedulerName || "New Schedule"}
        </title>
      </Helmet>

      <SaveSchedulerDialog
        document={saveDialog}
        onCancel={() => setSaveDialog(null)}
        onSuccess={handleSaveSuccess}
      />

      <ResetConfirmationDialog
        version={resetDialog}
        onConfirm={doReset}
        onClose={() => setResetDialog(false)}
      />

      {isFetching && <LinearProgress />}
      {isSchedulerDisabled(error) ? (
        <SchedulerDisabledBanner />
      ) : (
      <div className={classes.wrapper}>
        <Toolbar>
          <Text className={classes.name}>{schedulerName || "NEW"}</Text>

          {isModified ? (
            <Pill color="yellow" label="Modified" />
          ) : (
            <Pill label="Unmodified" />
          )}
          {!_.isEmpty(jsonErrors) && <Pill color="red" label="Validation" />}

          <div className={classes.rightButtons}>
            <Button
              disabled={!_.isEmpty(jsonErrors) || !isModified}
              onClick={handleOpenSave}
            >
              Save
            </Button>
            <Button
              disabled={!isModified}
              onClick={() => setResetDialog(true)}
              variant="secondary"
            >
              Reset
            </Button>
          </div>
        </Toolbar>

        <Editor
          height="100%"
          width="100%"
          theme="vs-light"
          language="json"
          value={schedulerJson}
          autoIndent={true}
          beforeMount={handleEditorWillMount}
          onMount={handleEditorDidMount}
          onValidate={handleValidate}
          onChange={handleChange}
          options={{
            selectOnLineNumbers: true,
            minimap: {
              enabled: false,
            },
          }}
        />
      </div>
      )}
    </>
  );
}

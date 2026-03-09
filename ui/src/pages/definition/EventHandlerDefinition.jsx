import React, { useMemo, useRef, useState } from "react";
import { useRouteMatch } from "react-router-dom";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import { Button, LinearProgress, Pill, Text } from "../../components";
import _ from "lodash";
import Editor from "@monaco-editor/react";
import { Toolbar } from "@material-ui/core";
import { configureMonaco, NEW_EVENT_HANDLER_TEMPLATE } from "../../schema/eventHandler";
import { useEventHandler } from "../../data/eventHandler";
import ResetConfirmationDialog from "./ResetConfirmationDialog";
import { usePushHistory } from "../../components/NavLink";
import SaveEventHandlerDialog from "./SaveEventHandlerDialog";

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

export default function EventHandlerDefinition() {
  const classes = useStyles();
  const match = useRouteMatch();
  const navigate = usePushHistory();

  const [isModified, setIsModified] = useState(false);
  const [jsonErrors, setJsonErrors] = useState([]);
  const [resetDialog, setResetDialog] = useState(false);
  const [saveDialog, setSaveDialog] = useState(null);

  const editorRef = useRef();
  const eventHandlerName = _.get(match, "params.name");
  const eventHandlerEvent = _.get(match, "params.event");

  const {
    eventHandler: eventHandlerDef,
    isFetching,
    refetch,
  } = useEventHandler(eventHandlerEvent, eventHandlerName, NEW_EVENT_HANDLER_TEMPLATE);

  console.log(eventHandlerDef);

  const eventHandlerJson = useMemo(
    () => (eventHandlerDef ? JSON.stringify(eventHandlerDef, null, 2) : ""),
    [eventHandlerDef]
  );

  const handleOpenSave = () => {
    setSaveDialog({
      original: eventHandlerName ? eventHandlerJson : "",
      originalObj: eventHandlerName ? eventHandlerDef : null,
      modified: editorRef.current.getModel().getValue(),
    });
  };

  const handleSaveSuccess = (event, name) => {
    setSaveDialog(null);
    setIsModified(false);

    if (name === eventHandlerName) {
      refetch();
    } else {
      navigate(`/eventHandlerDef/${event}/${name}`);
    }
  }

  // Reset
  const doReset = () => {
    editorRef.current.getModel().setValue(eventHandlerJson);

    setResetDialog(false);
    setIsModified(false);
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
    setIsModified(v !== eventHandlerJson);
  };

  return (
    <>
      <Helmet>
        <title>
          Conductor UI - Event Handler Definition - {eventHandlerName || "New Event Handler"}
        </title>
      </Helmet>

      <SaveEventHandlerDialog
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
      <div className={classes.wrapper}>
        <Toolbar>
          <Text className={classes.name}>{eventHandlerName || "NEW"}</Text>

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
          value={eventHandlerJson}
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
    </>
  );
}

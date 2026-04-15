import { useRef, useState, useMemo } from "react";
import {
  Dialog,
  Toolbar,
  FormControlLabel,
  Checkbox,
  Snackbar,
} from "@material-ui/core";
import Alert from "@material-ui/lab/Alert";
import { Text, Button, LinearProgress, Pill } from "../../components";
import { DiffEditor } from "@monaco-editor/react";
import { makeStyles } from "@material-ui/styles";
import {
  useSaveWorkflow,
  useWorkflowNamesAndVersions,
} from "../../data/workflow";
import _ from "lodash";
import { useEffect } from "react";

const useStyles = makeStyles({
  rightButtons: {
    display: "flex",
    flexGrow: 1,
    justifyContent: "flex-end",
    gap: 8,
  },
  toolbar: {
    paddingLeft: 20,
  },
});
//const WORKFLOW_SAVED_SUCCESSFULLY = "Workflow saved successfully.";
const WORKFLOW_SAVE_FAILED = "Failed to save the workflow definition.";

export default function SaveWorkflowDialog({ onSuccess, onCancel, document }) {
  const classes = useStyles();
  const diffMonacoRef = useRef(null);
  const [errorMsg, setErrorMsg] = useState();
  const [useAutoVersion, setUseAutoVersion] = useState(true);
  const { data: namesAndVersions } = useWorkflowNamesAndVersions();

  const modified = useMemo(() => {
    if (!document || !namesAndVersions) return { text: "", obj: null };

    const parsedModified = JSON.parse(document.modified);
    const latestVersion = _.get(
      _.last(namesAndVersions.get(parsedModified.name)),
      "version",
      0
    );

    if (useAutoVersion) {
      parsedModified.version = _.isNumber(latestVersion)
        ? latestVersion + 1
        : 1;
    }
    const isNew = _.get(document, "originalObj.name") !== parsedModified.name;
    const isClash = isNew && namesAndVersions.has(parsedModified.name);

    return {
      text: JSON.stringify(parsedModified, null, 2),
      obj: parsedModified,
      isClash: isClash,
      isNew: isNew,
    };
  }, [document, useAutoVersion, namesAndVersions]);

  useEffect(() => {
    if (modified.isClash) {
      setErrorMsg(
        "Cannot save workflow definition. Workflow name already in use."
      );
    } else {
      setErrorMsg(undefined);
    }
  }, [modified]);

  const { isLoading, mutate: saveWorkflow } = useSaveWorkflow({
    onSuccess: (data) => {
      console.log("onsuccess", data);
      onSuccess(modified.obj.name, modified.obj.version);
    },
    onError: (err) => {
      console.log("onerror", err);
      const errObj = JSON.parse(err);
      let errStr = errObj.validationErrors && errObj.validationErrors.length > 0
        ? `${errObj.validationErrors[0].message}: ${errObj.validationErrors[0].path}`
        : errObj.message;
      setErrorMsg(`${WORKFLOW_SAVE_FAILED} ${errStr}`);
    },
  });

  const handleSave = () => {
    saveWorkflow({ body: modified.obj, isNew: modified.isNew });
  };

  const diffEditorDidMount = (editor) => {
    diffMonacoRef.current = editor;
  };

  return (
    <Dialog
      fullScreen
      open={!!document}
      onClose={() => onCancel()}
      TransitionProps={{
        onEnter: () => setUseAutoVersion(true),
      }}
    >
      <Snackbar
        open={!!errorMsg}
        onClose={() => setErrorMsg(null)}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
        transitionDuration={{ exit: 0 }}
      >
        <Alert onClose={() => setErrorMsg(null)} severity="error">
          {errorMsg}
        </Alert>
      </Snackbar>

      {isLoading && <LinearProgress />}

      <Toolbar className={classes.toolbar}>
        <Text>
          Saving{" "}
          <span style={{ fontWeight: "bold" }}>
            {_.get(modified, "obj.name")}
          </span>
        </Text>

        {modified.isNew && <Pill label="New" color="yellow" />}

        <div className={classes.rightButtons}>
          <FormControlLabel
            control={
              <Checkbox
                checked={useAutoVersion}
                onChange={(e) => setUseAutoVersion(e.target.checked)}
                disabled={modified.isClash}
              />
            }
            label="Automatically set version"
          />
          <Button onClick={handleSave} disabled={modified.isClash}>
            Save
          </Button>
          <Button onClick={() => onCancel()} variant="secondary">
            Cancel
          </Button>
        </div>
      </Toolbar>

      {document && (
        <DiffEditor
          height={"100%"}
          width={"100%"}
          theme="vs-light"
          language="json"
          original={document.original}
          modified={modified.text}
          autoIndent={true}
          onMount={diffEditorDidMount}
          options={{
            selectOnLineNumbers: true,
            readOnly: true,
            minimap: {
              enabled: false,
            },
          }}
        />
      )}
    </Dialog>
  );
}

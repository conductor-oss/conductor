import { useEffect, useMemo, useRef, useState } from "react";
import { Dialog, Snackbar, Toolbar } from "@material-ui/core";
import Alert from "@material-ui/lab/Alert";
import { Button, LinearProgress, Pill, Text } from "../../components";
import { DiffEditor } from "@monaco-editor/react";
import { makeStyles } from "@material-ui/styles";
import _ from "lodash";
import { useSaveScheduler } from "../../data/scheduler";

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

const SAVE_FAILED = "Failed to save the schedule definition.";

export default function SaveSchedulerDialog({ onSuccess, onCancel, document }) {
  const classes = useStyles();
  const diffMonacoRef = useRef(null);
  const [errorMsg, setErrorMsg] = useState();

  const modified = useMemo(() => {
    if (!document) return { text: "" };

    try {
      const parsedModified = JSON.parse(document.modified);
      const modifiedName = parsedModified.name;
      const isNew = _.get(document, "originalObj.name") !== modifiedName;

      return {
        text: document.modified,
        obj: parsedModified,
        isNew: isNew,
      };
    } catch (e) {
      return { text: document.modified, parseError: true };
    }
  }, [document]);

  const { isLoading, mutate: saveScheduler } = useSaveScheduler({
    onSuccess: () => {
      onSuccess(modified.obj.name);
    },
    onError: (err) => {
      let errStr;
      try {
        const errObj = JSON.parse(err);
        errStr =
          errObj.validationErrors && errObj.validationErrors.length > 0
            ? `${errObj.validationErrors[0].message}: ${errObj.validationErrors[0].path}`
            : errObj.message;
      } catch (e) {
        errStr = err;
      }
      setErrorMsg({
        message: `${SAVE_FAILED} ${errStr}`,
        dismissible: true,
      });
    },
  });

  useEffect(() => {
    if (modified.parseError) {
      setErrorMsg({
        message: "Invalid JSON. Please fix syntax errors before saving.",
        dismissible: false,
      });
    } else {
      setErrorMsg(undefined);
    }
  }, [modified]);

  const handleSave = () => {
    saveScheduler({ body: modified.obj });
  };

  const diffEditorDidMount = (editor) => {
    diffMonacoRef.current = editor;
  };

  return (
    <Dialog fullScreen open={!!document} onClose={() => onCancel()}>
      <Snackbar
        open={!!errorMsg}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
        transitionDuration={{ exit: 0 }}
      >
        <Alert
          severity="error"
          onClose={_.get(errorMsg, "dismissible") ? () => setErrorMsg() : null}
        >
          {_.get(errorMsg, "message")}
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
          <Button onClick={handleSave} disabled={modified.parseError}>
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
          modified={document.modified}
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

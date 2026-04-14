import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
} from "@mui/material";
import { Suspense } from "react";
import { Editor } from "@monaco-editor/react";
import MuiButton from "components/ui/buttons/MuiButton";
import { modalStyles } from "components/ui/dialogs/Modal/commonStyles";
import { Close, Code as CodeIcon } from "@mui/icons-material";
import { defaultEditorOptions, type EditorOptions } from "shared/editor";
const editorOption: EditorOptions = {
  ...defaultEditorOptions,
  tabSize: 2,
  minimap: { enabled: false },
  quickSuggestions: true,
  scrollbar: {
    vertical: "auto",
    verticalScrollbarSize: 8,
  },
  formatOnType: true,
  readOnly: true,
  wordWrap: "on",
  scrollBeyondLastLine: false,
  automaticLayout: true,
  fixedOverflowWidgets: true,
};

export const PayloadModal = ({
  payload,
  handleClose,
  title = "Event Payload",
}: {
  payload: string;
  handleClose: () => void;
  title?: string;
}) => {
  const onClose = (
    _event: Event,
    reason: "backdropClick" | "escapeKeyDown" | "closeButtonClick",
  ) => {
    if (reason === "backdropClick") {
      return false;
    }
    handleClose();
  };

  return (
    <>
      <Dialog
        maxWidth="md"
        open
        onClose={onClose}
        sx={{
          ...modalStyles.dialog,
          ".MuiPaper-root": {
            width: "100%",
          },
        }}
      >
        <DialogTitle sx={modalStyles.title}>
          <CodeIcon />
          {title}
        </DialogTitle>
        <DialogContent>
          <Paper
            variant="outlined"
            sx={{
              flex: 1,
              padding: 3,
              fontFamily: 'Menlo, Monaco, "Courier New", monospace',
            }}
          >
            <Suspense fallback={<div>Loading...</div>}>
              <Editor
                theme={"vs-light"}
                height="500px"
                value={payload}
                saveViewState
                language={"json"}
                options={editorOption}
              />
            </Suspense>
          </Paper>
        </DialogContent>
        <DialogActions sx={{ background: "transparent", borderTop: "none" }}>
          <MuiButton
            color="primary"
            startIcon={<Close />}
            onClick={handleClose}
          >
            Close
          </MuiButton>
        </DialogActions>
      </Dialog>
    </>
  );
};

import { Editor } from "@monaco-editor/react";
import {
  CheckCircleOutlined as CheckCircleOutlinedIcon,
  Close,
  Code as CodeIcon,
  FileCopyOutlined as FileCopyOutlinedIcon,
} from "@mui/icons-material";
import {
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Tab,
  Tabs,
} from "@mui/material";
import MuiButton from "components/MuiButton";
import MuiTypography from "components/MuiTypography";
import { SnackbarMessage } from "components/SnackbarMessage";
import { Suspense, SyntheticEvent, useState } from "react";
import { defaultEditorOptions, type EditorOptions } from "shared/editor";
import { greyText } from "theme/tokens/colors";
import {
  ApiSearchModalProps,
  SupportedDisplayTypes,
} from "../../../shared/CodeModal/types";
import { modalStyles } from "../Modal/commonStyles";

const editorOption: EditorOptions = {
  ...defaultEditorOptions,
  tabSize: 2,
  minimap: { enabled: false },
  quickSuggestions: true,
  scrollbar: {
    vertical: "hidden",
  },
  formatOnType: true,
  readOnly: true,
  wordWrap: "on",
};

const ApiSearchModal = ({
  dialogTitle = "API Search",
  dialogHeaderText = "Here is the code for the search parameters that you selected.",
  code,
  handleClose,
  displayLanguage,
  onTabChange,
  languages,
}: ApiSearchModalProps) => {
  const onClose = (
    _event: Event,
    reason: "backdropClick" | "escapeKeyDown" | "closeButtonClick",
  ) => {
    if (reason === "backdropClick") {
      return false;
    }
    handleClose();
  };

  const [showAlert, setShowAlert] = useState(false);

  const handleChangeTab = (
    _event: SyntheticEvent,
    newValue: SupportedDisplayTypes,
  ) => {
    onTabChange(newValue);
  };

  const handleCopy = () => {
    if (code) {
      setShowAlert(true);
      navigator.clipboard.writeText(code);
    }
  };

  return (
    <>
      {showAlert && (
        <SnackbarMessage
          message="Copied to Clipboard"
          severity="success"
          onDismiss={() => setShowAlert(false)}
          anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
        />
      )}
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
          {dialogTitle}
          <Close sx={modalStyles.closeIcon} onClick={handleClose} />
        </DialogTitle>
        <DialogContent>
          <MuiTypography mb={3} sx={{ color: greyText }} px={3}>
            {dialogHeaderText}
          </MuiTypography>
          <Tabs
            value={displayLanguage}
            onChange={handleChangeTab}
            variant="scrollable"
            scrollButtons="auto"
            allowScrollButtonsMobile
          >
            {languages.map((language) => {
              return (
                <Tab
                  key={language}
                  label={
                    <Box
                      sx={{
                        display: "flex",
                        alignItems: "center",
                      }}
                    >
                      <MuiTypography
                        component="span"
                        fontWeight={600}
                        textTransform="capitalize"
                      >
                        {language}
                      </MuiTypography>
                    </Box>
                  }
                  value={language}
                />
              );
            })}
          </Tabs>
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
                value={code}
                saveViewState
                language={
                  displayLanguage === "curl" ? "shell" : displayLanguage
                }
                options={editorOption}
              />
            </Suspense>
          </Paper>
        </DialogContent>
        <DialogActions sx={{ background: "transparent", borderTop: "none" }}>
          <MuiButton
            color="secondary"
            onClick={handleCopy}
            startIcon={<FileCopyOutlinedIcon />}
          >
            Copy
          </MuiButton>
          <MuiButton
            color="primary"
            startIcon={<CheckCircleOutlinedIcon />}
            onClick={handleClose}
          >
            Done
          </MuiButton>
        </DialogActions>
      </Dialog>
    </>
  );
};
export { ApiSearchModal };
export type { ApiSearchModalProps };

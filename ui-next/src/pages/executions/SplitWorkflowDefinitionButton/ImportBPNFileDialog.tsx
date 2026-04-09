import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  IconButton,
  InputAdornment,
  Stack,
  Alert,
  FormControlLabel,
  Switch,
} from "@mui/material";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { UploadSimple, XCircle } from "@phosphor-icons/react";
import CodeBlockInput from "components/ui/inputs/CodeBlockInput";
import { useImportBPMWorkflow } from "./hook";
import { useRef } from "react";
import MuiTypography from "components/ui/MuiTypography";

export const ImportBPNFileDialog = ({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) => {
  const {
    onChangeFileContent,
    onUpload,
    onFileSelect,
    onDragEnter,
    onDragLeave,
    onDragOver,
    onDrop,
    onReset,
    onWorkflowNameChange,
    onOverWriteWorkflowToggle,
    selectedFile,
    fileContent,
    isDragging,
    isUploading,
    uploadError,
    workflowName,
    workflowNameError,
    overWriteWorkflow,
  } = useImportBPMWorkflow({ onClose });

  const fileInputRef = useRef<HTMLInputElement>(null);
  const handleReset = () => {
    onReset();
    onClose();
  };
  return (
    <Dialog
      open={open}
      onClose={handleReset}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: {
          backgroundColor: "white",
          borderRadius: "6px",
        },
      }}
    >
      <DialogTitle
        sx={{
          p: 3,
          pb: 2,
          backgroundColor: "white",
          borderBottom: "none",
        }}
      >
        <Stack direction="row" alignItems="center" gap={2}>
          <UploadSimple size={20} />
          <Box
            sx={{
              display: "flex",
              alignItems: "start",
              gap: 1,
              mb: 0.5,
              flexDirection: "column",
            }}
          >
            <Typography
              variant="h6"
              sx={{
                fontSize: "16px",
                fontWeight: 500,
              }}
            >
              IMPORT BPMN
            </Typography>
            <Typography
              variant="body2"
              sx={{
                color: "text.secondary",
                fontSize: "14px",
                fontWeight: 300,
              }}
            >
              Convert a BPMN file automatically into a workflow definition.
            </Typography>
          </Box>
        </Stack>
        <IconButton
          aria-label="close"
          onClick={handleReset}
          sx={{
            position: "absolute",
            right: 8,
            top: 8,
            color: (theme) => theme.palette.grey[500],
          }}
        >
          <XCircle size={20} />
        </IconButton>
      </DialogTitle>

      <DialogContent>
        <Stack direction="row" gap={1}>
          <Box sx={{ width: 10, height: "100%" }} />
          <Box
            sx={{ display: "flex", flexDirection: "column", gap: 2, flex: 1 }}
            pt={4}
          >
            <Box sx={{ position: "relative" }}>
              <ConductorInput
                fullWidth
                label="Select file"
                value={selectedFile}
                placeholder="No file selected"
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <Button
                        variant="text"
                        size="small"
                        onClick={() => fileInputRef.current?.click()}
                        sx={{
                          height: "32px",
                          minWidth: "80px",
                          ml: 1,
                        }}
                      >
                        Browse
                      </Button>
                    </InputAdornment>
                  ),
                  readOnly: true,
                }}
              />
              <input
                ref={fileInputRef}
                type="file"
                hidden
                accept=".bpmn"
                onChange={onFileSelect}
              />
            </Box>

            <Typography align="center" sx={{ my: 1 }}>
              OR
            </Typography>

            <Box
              sx={{
                border: `1px dashed ${isDragging ? "#2196f3" : "#ccc"}`,
                borderRadius: "4px",
                p: 3,
                textAlign: "center",
                color: "#666",
                backgroundColor: isDragging
                  ? "rgba(33, 150, 243, 0.08)"
                  : "transparent",
                transition: "all 0.2s ease",
                cursor: "pointer",
                "&:hover": {
                  borderColor: "#2196f3",
                  backgroundColor: "rgba(33, 150, 243, 0.08)",
                },
              }}
              onDragEnter={onDragEnter}
              onDragOver={onDragOver}
              onDragLeave={onDragLeave}
              onDrop={onDrop}
              onClick={() => fileInputRef.current?.click()}
            >
              {isDragging
                ? "Drop BPMN file here"
                : "drag & drop BPMN file here"}
            </Box>

            <Typography align="center" sx={{ my: 1 }}>
              OR
            </Typography>

            <CodeBlockInput
              language="xml"
              minHeight={120}
              languageLabel="XML"
              autoformat={true}
              value={fileContent}
              onChange={(value) => {
                onChangeFileContent(value);
              }}
              containerStyles={{
                marginTop: "8px",
              }}
            />

            <ConductorInput
              fullWidth
              label="Workflow Name"
              required
              value={workflowName}
              onChange={(e) => onWorkflowNameChange(e.target.value)}
              placeholder="Enter workflow name"
              error={!!workflowNameError}
              helperText={
                workflowNameError || "This will be used as the workflow name"
              }
              sx={{ mt: 4 }}
            />

            <Box>
              <MuiTypography pt={2} fontWeight={600} color={"#767676"}>
                Overwrite workflow
              </MuiTypography>

              <MuiTypography opacity={0.5} pt={1}>
                When enabled, any existing workflow with the same name will be
                overwritten.
              </MuiTypography>

              <FormControlLabel
                labelPlacement="top"
                checked={overWriteWorkflow}
                sx={{ mt: 2 }}
                control={
                  <Switch
                    color="primary"
                    onChange={onOverWriteWorkflowToggle}
                  />
                }
                label=""
              />
            </Box>
          </Box>
        </Stack>
      </DialogContent>

      <DialogActions
        sx={{
          px: 3,
          pb: 3,
          backgroundColor: "white",
          flexDirection: "column",
          alignItems: "flex-end",
        }}
      >
        {uploadError && (
          <Alert
            severity="error"
            sx={{
              width: "100%",
              mb: 2,
            }}
          >
            {uploadError}
          </Alert>
        )}
        <Box sx={{ display: "flex", gap: 1 }}>
          <Button
            onClick={handleReset}
            disabled={isUploading}
            startIcon={<XCircle size={16} />}
            sx={{
              backgroundColor: "white",
              color: "text.primary",
              border: "1px solid #ccc",
              borderRadius: "4px",
            }}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={onUpload}
            disabled={isUploading || !fileContent || !workflowName}
            startIcon={<UploadSimple size={16} />}
            id="bpmn-dialog-import-button"
          >
            {isUploading ? "Importing..." : "Import"}
          </Button>
        </Box>
      </DialogActions>
    </Dialog>
  );
};

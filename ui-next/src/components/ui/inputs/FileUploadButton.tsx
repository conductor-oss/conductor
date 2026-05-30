import AttachIcon from "@mui/icons-material/AttachFile";
import { Box, useTheme } from "@mui/material";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Button, { MuiButtonProps } from "components/ui/buttons/MuiButton";
import { ChangeEvent, ElementType } from "react";
import { colors } from "theme/tokens/variables";
import XCloseIcon from "../../icons/XCloseIcon";

export interface FileUploadButtonProps extends MuiButtonProps {
  value?: string;
  onChangeFile: (fileName: string, fileValue: string) => void;
  onClearFile?: () => void;
  accept?: string;
  component?: ElementType;
  label?: string;
  helperText?: string;
  error?: boolean;
}

const ACCEPTED_TYPES =
  ".json,application/json, .jks,application/octet-stream, .pem,application/x-pem-file, .creds,application/octet-stream";

export default function FileUploadButton({
  value,
  onChangeFile: handleChange,
  onClearFile,
  accept = ACCEPTED_TYPES,
  label,
  helperText,
  error,
  ...props
}: FileUploadButtonProps) {
  const theme = useTheme();

  const stylesForError = {
    border: "1px solid red",
    color: theme.palette.input.error,
    "&:hover": {
      border: "1px solid red",
      color: theme.palette.input.error,
      boxShadow: `3px 3px 0px 0px ${theme.palette.input.error}`,
    },
    "&:active": {
      color: colors.black,
      boxShadow: "0px",
      backgroundColor: theme.palette.input.error,
    },
    "&:focus": {
      boxShadow: "0px",
    },
  };

  const stylesWithoutTheError = {
    "&:active": {
      color: colors.black,
      boxShadow: "0px",
    },
    "&:focus": {
      boxShadow: "0px",
    },
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (files) {
      const firstFile = files[0];
      const reader = new FileReader();
      reader.readAsDataURL(firstFile);
      reader.onload = () => {
        handleChange(firstFile?.name, reader.result as string);
      };
    }
  };

  return (
    <>
      {value ? (
        <Stack
          flexDirection="row"
          gap={2}
          flexWrap="wrap"
          alignItems={"center"}
        >
          <Button color="secondary" component="label" {...props}>
            Change {label ?? "File"}
            <input
              hidden
              accept={accept}
              type="file"
              onChange={handleFileChange}
            />
          </Button>
          <Box>{value}</Box>
          {onClearFile && (
            <IconButton
              aria-label="clear value"
              onClick={onClearFile}
              edge="end"
            >
              <XCloseIcon color={colors.blueLightMode} />
            </IconButton>
          )}
        </Stack>
      ) : (
        <Stack
          flexDirection="row"
          gap={2}
          flexWrap="wrap"
          alignItems={"center"}
        >
          <Button
            color="secondary"
            component="label"
            sx={error ? stylesForError : stylesWithoutTheError}
            {...props}
          >
            <AttachIcon />
            {label ? `Choose ${label}` : "File Upload"}
            <input
              hidden
              accept={accept}
              type="file"
              onChange={handleFileChange}
            />
          </Button>
          <Box>No file chosen</Box>
        </Stack>
      )}
      <Box
        sx={{
          fontWeight: 300,
          fontSize: "11.14px",
          marginTop: "4px",
          paddingLeft: "8px",
          color: error ? theme.palette.input.error : colors.sidebarGrey,
        }}
      >
        {helperText}
      </Box>
    </>
  );
}

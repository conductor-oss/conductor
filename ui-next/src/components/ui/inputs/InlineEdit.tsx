import { Box, IconButton, TextField, Theme } from "@mui/material";
import {
  X as Cancel,
  Check,
  PencilSimple as EditIcon,
} from "@phosphor-icons/react";
import _isEmpty from "lodash/isEmpty";
import { ReactNode, useEffect, useState } from "react";

const additionalWidth = 15;

export const InlineEdit = ({
  value,
  editLabel = <EditIcon size={22} />,
  saveLabel = <Check size={22} />,
  cancelLabel = <Cancel size={22} />,
  flexGrow = 0,
  onSave,
  onChangeMode,
  error = false,
  helperText,
  notAllowedCharRegex,
  disabled = false,
}: {
  value: string;
  editLabel?: ReactNode;
  saveLabel?: ReactNode;
  cancelLabel?: ReactNode;
  flexGrow?: number;
  onSave: (val: string) => void;
  onChangeMode?: (edit: boolean) => void;
  error?: boolean;
  helperText?: string;
  notAllowedCharRegex?: RegExp;
  disabled?: boolean;
}) => {
  const [edit, setEdit] = useState(false);
  const [internalValue, setInternalValue] = useState("");

  useEffect(() => {
    setInternalValue(value);
  }, [value]);

  useEffect(() => {
    if (onChangeMode) {
      onChangeMode(edit);
    }
  }, [edit, onChangeMode]);

  const handleInputChange = (newValue: string) => {
    if (notAllowedCharRegex) {
      if (!notAllowedCharRegex.test(newValue)) {
        setInternalValue(newValue);
      }
    } else {
      setInternalValue(newValue);
    }
  };

  const disableSave = _isEmpty(internalValue?.trim());

  return (
    <Box display="flex" alignItems={edit ? "flex-end" : "center"}>
      {edit ? (
        <Box flexGrow={flexGrow}>
          <TextField
            fullWidth
            autoFocus
            value={internalValue}
            onChange={(e) => handleInputChange(e.target.value)}
            sx={{
              width: `${internalValue.length + additionalWidth}ch`,
              minWidth: "120px",
              maxWidth: "480px",
            }}
            error={error}
            helperText={helperText}
          ></TextField>
        </Box>
      ) : (
        <Box
          flexGrow={flexGrow}
          overflow="hidden"
          whiteSpace="nowrap"
          textOverflow="ellipsis"
          fontSize={16}
          sx={(theme: Theme) =>
            error
              ? {
                  border: `2px solid ${theme.palette.error.main}`,
                  borderRadius: "4px",
                  padding: 1,
                }
              : {}
          }
        >
          {internalValue}
        </Box>
      )}
      <Box ml={1} display="flex" sx={{ marginBottom: "auto" }}>
        {edit ? (
          <>
            <Box mr={1}>
              <IconButton
                onClick={() => {
                  if (internalValue !== value) {
                    onSave(internalValue);
                  }
                  setEdit(false);
                }}
                disabled={disableSave}
              >
                {saveLabel}
              </IconButton>
            </Box>
            <Box>
              <IconButton
                onClick={() => {
                  setInternalValue(value);
                  setEdit(false);
                }}
              >
                {cancelLabel}
              </IconButton>
            </Box>
          </>
        ) : (
          <IconButton
            onClick={() => setEdit(true)}
            sx={{ cursor: "pointer", marginTop: "-6px" }}
            disabled={disabled}
          >
            {editLabel}
          </IconButton>
        )}
      </Box>
    </Box>
  );
};

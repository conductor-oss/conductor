import { ChangeEvent, useEffect, useRef, useState } from "react";
import { ControlProps } from "@jsonforms/core";
import { withJsonFormsControlProps } from "@jsonforms/react";
import { Unwrapped } from "@jsonforms/material-renderers";
import {
  Box,
  Button,
  FormHelperText,
  InputAdornment,
  TextField,
  alpha,
} from "@mui/material";
import { FileZip, UploadSimple } from "@phosphor-icons/react";
import {
  MAX_ZIP_BYTES,
  SUMMARY_MIN_LENGTH,
  decodedSize,
  isZipBase64,
} from "./zipFile";

function ZipFileControl(props: ControlProps) {
  const { visible, enabled, path, schema, handleChange, data, label, errors } =
    props;
  const reader = useRef<FileReader | null>(null);
  const [reading, setReading] = useState(false);
  const [error, setError] = useState("");
  // The last removed ZIP, kept so the removal can be undone. The original file
  // may not be on the user's machine (e.g. it came from a saved workflow).
  const [removed, setRemoved] = useState<string | null>(null);

  // Discard an unfinished read when the operation or visible source changes.
  useEffect(() => {
    setReading(false);
    setError("");
    setRemoved(null);
    return () => {
      reader.current?.abort();
      reader.current = null;
    };
  }, [visible, path, schema]);

  // Once the field has a value again (typed, pasted or chosen), the removed
  // ZIP is no longer what the user would expect "Undo" to bring back.
  useEffect(() => {
    if (data) setRemoved(null);
  }, [data]);

  if (!visible) return null;

  const chooseFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setError("");
    if (!file.name.toLowerCase().endsWith(".zip")) {
      setError("Choose a .zip file.");
      return;
    }
    if (file.size === 0 || file.size > MAX_ZIP_BYTES) {
      setError(
        "Choose a non-empty ZIP up to 10 MiB. Use a cloud location for larger packages.",
      );
      return;
    }
    reader.current?.abort();
    const active = new FileReader();
    reader.current = active;
    setReading(true);
    active.onload = () => {
      if (reader.current !== active) return;
      setReading(false);
      reader.current = null;
      // Only the Base64 bytes belong in task inputs, not the data URL or filename.
      const encoded =
        typeof active.result === "string"
          ? active.result.split(",")[1]
          : undefined;
      if (
        !encoded ||
        (schema.maxLength !== undefined && encoded.length > schema.maxLength)
      ) {
        setError("The ZIP exceeds this field's size limit.");
        return;
      }
      setRemoved(null);
      handleChange(path, encoded);
    };
    active.onerror = () => {
      if (reader.current !== active) return;
      reader.current = null;
      setReading(false);
      setError("Unable to read the ZIP. Choose it again.");
    };
    active.readAsDataURL(file);
  };

  // Show a summary for anything that is a ZIP, however small, so chosen files
  // always look the same. Long values that aren't recognizably a ZIP are
  // summarized too, since rendering them in a text field is slow.
  const summarized =
    typeof data === "string" &&
    (isZipBase64(data) || data.length >= SUMMARY_MIN_LENGTH);
  const validationError = summarized ? errors : "";
  const canUndoRemoval = removed !== null && !data;
  const showHelperText = Boolean(error || validationError) || !summarized;

  const removeZip = () => {
    setError("");
    setRemoved(data);
    handleChange(path, undefined);
  };

  const undoRemoval = () => {
    if (removed === null) return;
    handleChange(path, removed);
    setRemoved(null);
  };

  const fileInput = (
    <input
      hidden
      type="file"
      aria-label={`Choose ZIP for ${label}`}
      accept=".zip,application/zip"
      disabled={!enabled || reading}
      onChange={chooseFile}
    />
  );

  return (
    <Box>
      {summarized ? (
        // Rendered as a read-only field so the label, border and height match
        // the neighbouring inputs; the actions sit inside it like adornments.
        <TextField
          fullWidth
          label={label}
          value={reading ? "Reading ZIP…" : `ZIP file · ${decodedSize(data)}`}
          error={Boolean(validationError)}
          disabled={!enabled}
          sx={{
            // Keep the theme's field spacing unless helper text sits below.
            mb: showHelperText ? 0 : "16px",
            // The form theme pads inputs 14px top / 8px bottom to clear the
            // floating label. Balance it here (same total height) so the
            // value lines up with the centred icon and buttons.
            "& .MuiInputBase-input": { pt: "11px", pb: "11px" },
          }}
          slotProps={{
            inputLabel: { shrink: true },
            input: {
              readOnly: true,
              startAdornment: (
                <InputAdornment position="start">
                  <FileZip size={18} aria-hidden style={{ display: "block" }} />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end" sx={{ gap: 1 }}>
                  <Button
                    component="label"
                    variant="contained"
                    size="small"
                    disabled={!enabled || reading}
                  >
                    Replace
                    {fileInput}
                  </Button>
                  <Button
                    variant="text"
                    size="small"
                    disabled={!enabled || reading}
                    onClick={removeZip}
                    // The app theme unsets text-button hover; restore MUI's
                    // tint so Remove reads as clickable.
                    sx={{
                      "&:hover": {
                        backgroundColor: (theme) =>
                          alpha(
                            theme.palette.primary.main,
                            theme.palette.action.hoverOpacity,
                          ),
                      },
                    }}
                  >
                    Remove
                  </Button>
                </InputAdornment>
              ),
            },
          }}
        />
      ) : (
        <>
          <Unwrapped.MaterialTextControl
            {...props}
            enabled={enabled && !reading}
          />
          <Button
            component="label"
            variant="contained"
            color="secondary"
            size="small"
            startIcon={<UploadSimple size={16} aria-hidden />}
            disabled={!enabled || reading}
          >
            {reading ? "Reading ZIP…" : "Choose ZIP"}
            {fileInput}
          </Button>
        </>
      )}
      {canUndoRemoval && !error ? (
        <FormHelperText component="div" role="status">
          ZIP removed.{" "}
          <Button
            variant="text"
            size="small"
            disabled={!enabled}
            onClick={undoRemoval}
            sx={{ minHeight: "unset", height: "auto", p: 0, minWidth: 0 }}
          >
            Undo
          </Button>
        </FormHelperText>
      ) : showHelperText ? (
        // A loaded ZIP needs no guidance; only surface problems there.
        <FormHelperText
          error={Boolean(error || validationError)}
          role={error ? "alert" : undefined}
        >
          {error ||
            validationError ||
            "Choose a ZIP up to 10 MiB, or paste Base64 above. Larger packages require a cloud location."}
        </FormHelperText>
      ) : null}
    </Box>
  );
}

const ZipFileControlRenderer = withJsonFormsControlProps(ZipFileControl);

export default ZipFileControlRenderer;

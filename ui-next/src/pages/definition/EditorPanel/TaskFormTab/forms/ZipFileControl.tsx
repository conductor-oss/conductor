import { ChangeEvent, useEffect, useRef, useState } from "react";
import {
  ControlProps,
  and,
  isStringControl,
  rankWith,
  schemaMatches,
} from "@jsonforms/core";
import { withJsonFormsControlProps } from "@jsonforms/react";
import { Unwrapped } from "@jsonforms/material-renderers";
import { Box, Button, FormHelperText } from "@mui/material";

const MAX_ZIP_BYTES = 10 * 1024 * 1024;

function ZipFileControl(props: ControlProps) {
  const { visible, enabled, path, schema, handleChange } = props;
  const reader = useRef<FileReader | null>(null);
  const [reading, setReading] = useState(false);
  const [error, setError] = useState("");

  // Discard an unfinished read when the operation or visible source changes.
  useEffect(() => {
    setReading(false);
    setError("");
    return () => {
      reader.current?.abort();
      reader.current = null;
    };
  }, [visible, path, schema]);

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

  return (
    <Box>
      <Unwrapped.MaterialTextControl {...props} enabled={enabled && !reading} />
      <Button
        component="label"
        variant="outlined"
        disabled={!enabled || reading}
      >
        {reading ? "Reading ZIP…" : "Choose ZIP"}
        <input
          hidden
          type="file"
          aria-label={`Choose ZIP for ${props.label}`}
          accept=".zip,application/zip"
          disabled={!enabled || reading}
          onChange={chooseFile}
        />
      </Button>
      <FormHelperText error={Boolean(error)} role={error ? "alert" : undefined}>
        {error ||
          "Choose a ZIP up to 10 MiB, or paste Base64 above. Larger packages require a cloud location."}
      </FormHelperText>
    </Box>
  );
}

export const zipFileRenderer = {
  tester: rankWith(
    5,
    and(
      isStringControl,
      schemaMatches(
        (schema) =>
          "contentEncoding" in schema &&
          "contentMediaType" in schema &&
          schema.contentEncoding === "base64" &&
          schema.contentMediaType === "application/zip",
      ),
    ),
  ),
  renderer: withJsonFormsControlProps(ZipFileControl),
};

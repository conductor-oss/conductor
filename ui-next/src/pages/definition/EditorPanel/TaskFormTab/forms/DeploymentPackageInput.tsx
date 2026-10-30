import {
  Alert,
  Box,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import Button from "components/ui/buttons/MuiButton";
import { useEffect, useRef, useState } from "react";
import {
  readDeploymentZip,
  withoutDeploymentPackage,
} from "utils/deploymentPackage";

type Props = {
  input: Record<string, any>;
  onChange: (input: Record<string, any>) => void;
};

export default function DeploymentPackageInput({ input, onChange }: Props) {
  // Source selection is a UI concern. Persist only the provider's package fields.
  const [inlineMode, setInlineMode] = useState("upload");
  const [fileName, setFileName] = useState("");
  const [error, setError] = useState("");
  const [reading, setReading] = useState(false);
  const sequence = useRef(0);
  const latestInput = useRef(input);
  latestInput.current = input;
  useEffect(
    () => () => {
      sequence.current++;
    },
    [],
  );
  const source =
    input.s3Bucket != null ||
    input.s3Key != null ||
    input.s3ObjectVersion != null
      ? "s3"
      : inlineMode;
  const changeSource = (next: string) => {
    sequence.current++;
    setReading(false);
    setError("");
    setFileName("");
    setInlineMode(next === "s3" ? "upload" : next);
    // Upload and Base64 share the same bytes; changing presentation must not discard them.
    if ((source === "s3") !== (next === "s3"))
      onChange({
        ...withoutDeploymentPackage(input),
        ...(next === "s3" ? { s3Bucket: "", s3Key: "" } : {}),
      });
  };

  return (
    <Stack spacing={2} sx={{ mb: 2 }}>
      <Typography variant="subtitle2">Deployment package</Typography>
      <TextField
        select
        label="Package source"
        value={source}
        onChange={(event) => changeSource(event.target.value)}
      >
        <MenuItem value="upload">Upload ZIP</MenuItem>
        <MenuItem value="s3">S3 object</MenuItem>
        <MenuItem value="base64">Base64 / expression (advanced)</MenuItem>
      </TextField>
      {source === "s3" ? (
        <>
          <Typography variant="body2">
            Use an existing ZIP object in the same AWS region as the function.
            No file is uploaded to S3 by this form.
          </Typography>
          {(["s3Bucket", "s3Key", "s3ObjectVersion"] as const).map(
            (field, index) => (
              <TextField
                key={field}
                label={
                  [
                    "S3 bucket",
                    "S3 object key",
                    "S3 object version (optional)",
                  ][index]
                }
                required={index < 2}
                value={input[field] ?? ""}
                onChange={(event) => {
                  const next = { ...input };
                  if (field === "s3ObjectVersion" && !event.target.value)
                    delete next[field];
                  else next[field] = event.target.value;
                  onChange(next);
                }}
              />
            ),
          )}
        </>
      ) : source === "base64" ? (
        <TextField
          label="ZIP Base64 or expression"
          required
          multiline
          minRows={3}
          value={input.zipFileBase64 ?? ""}
          onChange={(event) =>
            onChange({ ...input, zipFileBase64: event.target.value })
          }
          helperText="Paste raw Base64 (without a data URL prefix), or a workflow expression. Use S3 for larger packages."
        />
      ) : (
        <Box>
          <Button component="label" color="secondary" disabled={reading}>
            {reading ? "Reading ZIP…" : "Choose ZIP"}
            <input
              hidden
              type="file"
              accept=".zip,application/zip,application/x-zip-compressed"
              aria-label="Upload deployment ZIP"
              disabled={reading}
              onChange={async (event) => {
                const file = event.target.files?.[0];
                event.target.value = "";
                if (!file) return;
                const current = ++sequence.current;
                setError("");
                setReading(true);
                try {
                  const base64 = await readDeploymentZip(file);
                  if (current !== sequence.current) return;
                  setFileName(file.name);
                  onChange({
                    ...withoutDeploymentPackage(latestInput.current),
                    zipFileBase64: base64,
                  });
                } catch (failure) {
                  if (current === sequence.current)
                    setError((failure as Error).message);
                } finally {
                  if (current === sequence.current) setReading(false);
                }
              }}
            />
          </Button>
          <Typography variant="body2">
            {fileName ||
              (input.zipFileBase64
                ? "An inline package or expression is attached."
                : "No ZIP selected.")}
          </Typography>
          <Typography variant="caption">
            Maximum 1 MiB. ZIP content is embedded in the workflow definition;
            use S3 for larger artifacts.
          </Typography>
          {input.zipFileBase64 && (
            <Button
              color="secondary"
              onClick={() => {
                sequence.current++;
                setReading(false);
                setFileName("");
                setError("");
                onChange(withoutDeploymentPackage(input));
              }}
            >
              Remove ZIP
            </Button>
          )}
        </Box>
      )}
      {error && (
        <Alert severity="error">
          {error} Existing package input was not replaced.
        </Alert>
      )}
    </Stack>
  );
}

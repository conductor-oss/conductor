import { Box, Stack } from "@mui/material";
import {
  ArrowsClockwiseIcon,
  DownloadSimpleIcon,
  FileTextIcon,
  UploadSimpleIcon,
  XIcon,
} from "@phosphor-icons/react";
import { ChangeEvent, ReactNode, useState } from "react";

import { Button, Text } from "components";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import { colors } from "theme/tokens/variables";

/**
 * Where the rules file comes from: a location the task resolves at runtime, or a file uploaded
 * and held by the server.
 *
 * DESIGN ONLY. Nothing here talks to a server yet — the upload endpoint, and what an uploaded
 * file leaves behind in `inputParameters.ruleFileLocation`, are still being decided. Until that
 * lands the chosen file is kept in local state purely so the states can be seen and reviewed,
 * and the source is not persisted on the task at all.
 */

type Source = "link" | "upload";

const SOURCES = [
  { value: "link", label: "Link" },
  { value: "upload", label: "Upload file" },
];

const ACCEPTED_FILE_TYPES = ".xls,.xlsx,.csv,.drl";

const formatSize = (bytes: number) => {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

type Props = {
  /** The link form of the value — a URL or a workflow variable. */
  value: string;
  onChange: (value: string) => void;
};

export function RuleFileField({ value, onChange }: Props) {
  const [source, setSource] = useState<Source>("link");
  const [file, setFile] = useState<File | null>(null);

  const pickFile = (event: ChangeEvent<HTMLInputElement>) => {
    const chosen = event.target.files?.[0];
    if (chosen) {
      setFile(chosen);
    }
    // Let the same file be chosen again after it is removed.
    event.target.value = "";
  };

  // The empty state leads with this, so it is the prominent one there; beside a chosen file it
  // sits with Download and Remove and has to match them.
  const uploadButton = (
    label: string,
    icon: ReactNode,
    variant: "outlined" | "text",
  ) => (
    <Button
      id="rule-file-upload-btn"
      variant={variant}
      size="small"
      component="label"
      startIcon={icon}
    >
      {label}
      <input
        hidden
        type="file"
        accept={ACCEPTED_FILE_TYPES}
        onChange={pickFile}
      />
    </Button>
  );

  return (
    <Box>
      <Text
        sx={{
          fontSize: "12.5px",
          fontWeight: 600,
          color: colors.gray04,
        }}
      >
        Rule file source
      </Text>
      <RadioButtonGroup
        ariaLabel="Rule file source"
        name="rule-file-source"
        items={SOURCES}
        value={source}
        onChange={(_event, next) => setSource(next as Source)}
      />

      <Box sx={{ marginTop: "10px" }}>
        {source === "link" ? (
          <ConductorAutocompleteVariables
            value={value}
            label="Rule file location"
            onChange={onChange}
          />
        ) : file ? (
          <Box
            sx={{
              display: "flex",
              alignItems: "flex-start",
              gap: "12px",
              padding: "16px",
              borderRadius: "8px",
              border: `1px solid ${colors.gray11}`,
            }}
          >
            <FileTextIcon size={22} color={colors.gray06} />
            <Box sx={{ flexGrow: 1, overflow: "hidden" }}>
              <Text
                sx={{
                  fontSize: "14px",
                  fontWeight: 600,
                  overflowWrap: "anywhere",
                }}
              >
                {file.name}
              </Text>
              <Text sx={{ fontSize: "12.5px", color: colors.gray06 }}>
                {formatSize(file.size)}
              </Text>
              <Stack
                direction="row"
                spacing={0}
                sx={{ marginTop: "8px", flexWrap: "wrap", marginLeft: "-6px" }}
              >
                <Button
                  id="rule-file-download-btn"
                  variant="text"
                  size="small"
                  startIcon={<DownloadSimpleIcon size={16} />}
                >
                  Download
                </Button>
                {uploadButton(
                  "Replace",
                  <ArrowsClockwiseIcon size={16} />,
                  "text",
                )}
                <Button
                  id="rule-file-remove-btn"
                  variant="text"
                  size="small"
                  startIcon={<XIcon size={16} />}
                  onClick={() => setFile(null)}
                >
                  Remove
                </Button>
              </Stack>
            </Box>
          </Box>
        ) : (
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "flex-start",
              gap: "8px",
              padding: "20px",
              borderRadius: "8px",
              border: `1px dashed ${colors.gray11}`,
            }}
          >
            {uploadButton(
              "Upload rule file",
              <UploadSimpleIcon size={16} />,
              "outlined",
            )}
            <Text sx={{ fontSize: "12.5px", color: colors.gray06 }}>
              Excel, CSV or Drools rules file.
            </Text>
          </Box>
        )}
      </Box>
    </Box>
  );
}

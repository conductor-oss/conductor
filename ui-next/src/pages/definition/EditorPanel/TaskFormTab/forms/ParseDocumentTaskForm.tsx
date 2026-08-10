import {
  Alert,
  AlertTitle,
  Box,
  Chip,
  FormHelperText,
  Grid,
  Slider,
  Stack,
  Typography,
} from "@mui/material";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { useCallback, useState } from "react";
import { updateField } from "utils/fieldHelpers";
import { useGetIntegration } from "utils/hooks";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

// Media type options for PARSE_DOCUMENT with comprehensive document types
const PARSE_DOCUMENT_MEDIA_TYPES = [
  { value: "auto", label: "Auto-detect (Recommended)" },
  // Office Documents
  {
    value:
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    label: "Word Document (.docx)",
  },
  {
    value: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    label: "Excel Spreadsheet (.xlsx)",
  },
  {
    value:
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    label: "PowerPoint Presentation (.pptx)",
  },
  {
    value: "application/msword",
    label: "Word Document (.doc)",
  },
  {
    value: "application/vnd.ms-excel",
    label: "Excel Spreadsheet (.xls)",
  },
  {
    value: "application/vnd.ms-powerpoint",
    label: "PowerPoint Presentation (.ppt)",
  },
  // PDF
  {
    value: "application/pdf",
    label: "PDF Document",
  },
  // HTML
  {
    value: "text/html",
    label: "HTML",
  },
  // Images (with OCR)
  {
    value: "image/jpeg",
    label: "JPEG Image (OCR)",
  },
  {
    value: "image/png",
    label: "PNG Image (OCR)",
  },
  {
    value: "image/gif",
    label: "GIF Image (OCR)",
  },
  {
    value: "image/bmp",
    label: "BMP Image (OCR)",
  },
  {
    value: "image/tiff",
    label: "TIFF Image (OCR)",
  },
  // Zipped Documents
  {
    value: "application/zip",
    label: "ZIP Archive (Auto-extract)",
  },
  {
    value: "application/x-zip-compressed",
    label: "ZIP Compressed (Auto-extract)",
  },
  // Text Formats
  {
    value: "text/plain",
    label: "Plain Text",
  },
  {
    value: "text/markdown",
    label: "Markdown",
  },
];

// Media type configuration types
type MediaTypeConfigItem = {
  description: string;
  chunkSizeRecommendation: string;
  matchers?: readonly string[];
  matchType?: "includes" | "startsWith";
};

// Media type configuration for descriptions and recommendations
const MEDIA_TYPE_CONFIG: Record<string, MediaTypeConfigItem> = {
  auto: {
    description:
      "Document type will be automatically detected based on content and file extension. All content will be converted to Markdown format optimized for LLM processing.",
    chunkSizeRecommendation:
      "Default: 0 (no chunking). Set to positive value to enable semantic chunking of parsed content",
  },
  office: {
    description:
      "Office document will be parsed and converted to Markdown format, preserving document structure, formatting, tables, and hierarchies.",
    chunkSizeRecommendation:
      "Recommended: 1500-2000 characters for document content to maintain context",
    matchers: [
      "openxmlformats",
      "msword",
      "ms-excel",
      "ms-powerpoint",
    ] as const,
  },
  pdf: {
    description:
      "PDF will be parsed with text extraction, OCR for scanned documents, and converted to Markdown while preserving structure.",
    chunkSizeRecommendation:
      "Recommended: 1500-2000 characters for document content to maintain context",
    matchers: ["application/pdf"] as const,
  },
  html: {
    description:
      "HTML content will be parsed and converted to clean Markdown format, removing scripts and styling while preserving semantic structure.",
    chunkSizeRecommendation:
      "Default: 0 (no chunking). Set to positive value to enable semantic chunking of parsed content",
    matchers: ["text/html"] as const,
  },
  image: {
    description:
      "Image will be processed with OCR (Optical Character Recognition) to extract text content and convert to Markdown format.",
    chunkSizeRecommendation:
      "Recommended: 1000-1500 characters for OCR-extracted text",
    matchers: ["image/"] as const,
    matchType: "startsWith" as const,
  },
  zip: {
    description:
      "ZIP archive will be automatically extracted and all supported documents inside will be parsed and converted to Markdown.",
    chunkSizeRecommendation:
      "Recommended: 1500-2000 characters per document in archive",
    matchers: ["zip"] as const,
  },
  text: {
    description:
      "Text content will be parsed and converted to Markdown format with appropriate formatting.",
    chunkSizeRecommendation:
      "Default: 0 (no chunking). Set to positive value to enable semantic chunking of parsed content",
    matchers: ["text/"] as const,
    matchType: "startsWith" as const,
  },
  default: {
    description:
      "Content will be parsed and converted to Markdown format for optimal LLM processing.",
    chunkSizeRecommendation:
      "Default: 0 (no chunking). Set to positive value to enable semantic chunking of parsed content",
  },
};

// Helper function to find matching config
const findMediaTypeConfig = (mediaType: string) => {
  if (!mediaType || mediaType === "auto") {
    return MEDIA_TYPE_CONFIG.auto;
  }

  // Check each config type
  for (const config of Object.values(MEDIA_TYPE_CONFIG)) {
    if (!config.matchers) continue;

    const matchType = config.matchType || "includes";
    const matches = config.matchers.some((matcher) => {
      if (matchType === "startsWith") {
        return mediaType.startsWith(matcher);
      }
      return mediaType.includes(matcher);
    });

    if (matches) return config;
  }

  return MEDIA_TYPE_CONFIG.default;
};

// Get description of what happens based on media type
const getMediaTypeDescription = (mediaType: string): string => {
  return findMediaTypeConfig(mediaType).description;
};

// Get chunk size recommendation based on media type
const getChunkSizeRecommendation = (mediaType: string): string => {
  return findMediaTypeConfig(mediaType).chunkSizeRecommendation;
};

// Estimate chunk count
const estimateChunkCount = (
  chunkSize: number,
  contentLength: number = 0,
): string => {
  if (!chunkSize || chunkSize <= 0)
    return "No chunking (entire document as one piece)";
  if (!contentLength) return "Depends on document size";
  return `Approximately ${Math.ceil(contentLength / chunkSize)} chunks`;
};

export const ParseDocumentTaskForm = ({ task, onChange }: TaskFormProps) => {
  // Get current values from task
  const integrationName = task.inputParameters?.integrationName || "";
  const url = task.inputParameters?.url || "";
  const mediaType = task.inputParameters?.mediaType || "auto";
  const chunkSize = task.inputParameters?.chunkSize || 0;

  // need to fetch compatible integration names and pass them to the integrationName autocomplete options
  const integrations = useGetIntegration({});

  // Filter integrations to only include git, aws, and gcp types
  const integrationNameOptions =
    integrations?.data
      ?.filter((integration) => {
        const type = integration?.type?.toLowerCase() || "";
        // Filter by type containing git, aws,
        return type === "git" || type === "aws";
      })
      ?.map((integration) => integration?.name) || [];

  // Local state for URL validation
  const [urlError, setUrlError] = useState<string>("");

  // Validate URL format
  const validateUrl = useCallback((urlValue: string) => {
    if (!urlValue) {
      setUrlError("");
      return true;
    }

    // Allow template variables and JSON path expressions
    if (urlValue.includes("${") || urlValue.includes("$.")) {
      setUrlError("");
      return true;
    }

    // Basic URL validation
    const urlPatterns = [
      /^s3:\/\/.+/i,
      /^gs:\/\/.+/i,
      /^https?:\/\/.+/i,
      /^file:\/\/.+/i,
      /^git:\/\/.+/i,
    ];

    const isValid = urlPatterns.some((pattern) => pattern.test(urlValue));
    if (!isValid) {
      setUrlError(
        "Invalid URL format. Must use s3://, gs://, https://, http://, file://, or git:// protocol",
      );
      return false;
    }

    setUrlError("");
    return true;
  }, []);

  // Handlers
  const handleIntegrationNameChange = useCallback(
    (value: string) => {
      onChange(updateField("inputParameters.integrationName", value, task));
    },
    [onChange, task],
  );

  const handleUrlChange = useCallback(
    (value: string) => {
      validateUrl(value);
      onChange(updateField("inputParameters.url", value, task));
    },
    [onChange, task, validateUrl],
  );

  const handleMediaTypeChange = useCallback(
    (value: string) => {
      onChange(updateField("inputParameters.mediaType", value || "auto", task));
    },
    [onChange, task],
  );

  const handleChunkSizeChange = useCallback(
    (value: number) => {
      onChange(updateField("inputParameters.chunkSize", value, task));
    },
    [onChange, task],
  );

  // Slider marks for chunk size
  const sliderMarks = [
    { value: 0, label: "0" },
    { value: 2500, label: "2.5K" },
    { value: 5000, label: "5K" },
    { value: 7500, label: "7.5K" },
    { value: 10000, label: "10K" },
  ];

  return (
    <Box padding={1} width="100%">
      {/* Document Source Section */}
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Document Source"
      >
        <Grid container spacing={3} mt={3} mb={2}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              openOnFocus
              onChange={handleIntegrationNameChange}
              value={integrationName}
              otherOptions={integrationNameOptions}
              label="Integration Name"
              helperText="Select an integration that requires authentication (S3, Git, etc.). Leave empty if URL is publicly accessible."
            />
          </Grid>

          {integrationName && (
            <Grid size={12}>
              <Alert severity="info" sx={{ mb: 4, fontSize: 13 }}>
                <AlertTitle>Integration Selected: {integrationName}</AlertTitle>
                Make sure your integration credentials are configured and
                active. You can manage integrations in the Integrations page.
              </Alert>
            </Grid>
          )}

          <Grid size={12}>
            <ConductorAutocompleteVariables
              openOnFocus
              onChange={handleUrlChange}
              value={url}
              label="Document URL"
              error={!!urlError}
              helperText={
                urlError || "Enter the URL to the document or archive to parse"
              }
            />
          </Grid>

          {integrationName && (
            <Grid size={12}>
              <Box>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  URL Format Examples:
                </Typography>
                <Stack direction="column" spacing={1}>
                  {[
                    "s3://bucket/document.pdf",
                    "https://example.com/document.pdf",
                    "file:///path/to/document.pdf",
                  ].map((example, idx) => (
                    <Typography
                      key={idx}
                      variant="body2"
                      sx={{
                        fontFamily: "monospace",
                        fontSize: "12px",
                        color: "text.secondary",
                        pl: 2,
                      }}
                    >
                      • {example}
                    </Typography>
                  ))}
                </Stack>
              </Box>
            </Grid>
          )}
        </Grid>
      </TaskFormSection>

      {/* Media Type Section */}
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Media Type"
      >
        <Grid container spacing={3} mt={3} mb={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              openOnFocus
              onChange={handleMediaTypeChange}
              value={mediaType}
              otherOptions={PARSE_DOCUMENT_MEDIA_TYPES.map((opt) => opt.value)}
              label="Media Type"
              helperText="Select document type or use auto-detect. All documents will be converted to Markdown format."
              getOptionLabel={(option) =>
                PARSE_DOCUMENT_MEDIA_TYPES.find((opt) => opt.value === option)
                  ?.label ?? option.toString()
              }
              renderOption={(props, option) => (
                <Box component="li" {...props}>
                  <Typography>
                    {PARSE_DOCUMENT_MEDIA_TYPES.find(
                      (opt) => opt.value === option,
                    )?.label ?? option}
                  </Typography>
                </Box>
              )}
            />
          </Grid>

          <Grid size={12}>
            <Box
              sx={{
                marginTop: "8px",
                padding: "24px",
                backgroundColor: "#edf3fb",
                borderRadius: "8px",
                border: "1px solid #4B7BFB",
                color: "#194093",
              }}
            >
              <Typography component="span" sx={{ fontWeight: "bold" }}>
                Processing:{" "}
              </Typography>
              <Typography component="span">
                {getMediaTypeDescription(mediaType)}
              </Typography>
            </Box>
          </Grid>

          <Grid size={12}>
            <Box>
              <Typography
                variant="body2"
                color="text.secondary"
                gutterBottom
                sx={{ fontWeight: "bold" }}
              >
                Supported Formats:
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" gap={1}>
                <Chip label="Office Docs" size="small" variant="outlined" />
                <Chip label="PDF" size="small" variant="outlined" />
                <Chip label="HTML" size="small" variant="outlined" />
                <Chip label="Images (OCR)" size="small" variant="outlined" />
                <Chip label="ZIP Archives" size="small" variant="outlined" />
                <Chip label="Text Files" size="small" variant="outlined" />
              </Stack>
            </Box>
          </Grid>
        </Grid>
      </TaskFormSection>

      {/* Chunk Size Section */}
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Chunking Configuration"
      >
        <Grid container spacing={3} mt={3} mb={3}>
          <Grid size={12}>
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, md: 6 }}>
                <ConductorInput
                  label="Chunk Size (characters)"
                  name="chunkSize"
                  type="number"
                  value={chunkSize}
                  onTextInputChange={(value) => {
                    const numValue = parseInt(value, 10);
                    if (
                      !isNaN(numValue) &&
                      numValue >= 0 &&
                      numValue <= 10000
                    ) {
                      handleChunkSizeChange(numValue);
                    }
                  }}
                  fullWidth
                  error={chunkSize < 0 || chunkSize > 10000}
                  helperText="Enter 0 for no chunking, or a value between 100 and 10000 for semantic chunking"
                  inputProps={{ min: 0, max: 10000 }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <Box sx={{ mt: -0.5 }}>
                  <Typography
                    component={"span"}
                    variant="body2"
                    color="text.secondary"
                    gutterBottom
                    sx={{ fontWeight: "bold" }}
                  >
                    Result:{" "}
                  </Typography>
                  <Typography
                    component={"span"}
                    variant="body2"
                    color="text.secondary"
                    gutterBottom
                  >
                    {estimateChunkCount(chunkSize)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {chunkSize === 0
                      ? "The entire document will be returned as a single Markdown output"
                      : `Document will be split into semantic chunks of ~${chunkSize} characters`}
                  </Typography>
                </Box>
              </Grid>
            </Grid>
          </Grid>

          <Grid size={12}>
            <Box sx={{ px: 2 }}>
              <Slider
                value={chunkSize}
                onChange={(_, value) => handleChunkSizeChange(value as number)}
                min={0}
                max={10000}
                step={100}
                marks={sliderMarks}
                valueLabelDisplay="auto"
                sx={{ mt: 2 }}
              />
            </Box>
          </Grid>

          <Grid size={12}>
            <FormHelperText>
              {getChunkSizeRecommendation(mediaType)}
            </FormHelperText>
          </Grid>

          <Grid size={12}>
            <Box
              sx={{
                backgroundColor: "#fff9e6",
                borderRadius: "8px",
                border: "1px solid #ffc107",
                marginTop: "8px",
                padding: "24px",
                color: "#663c00",
              }}
            >
              <Typography
                component="span"
                sx={{ color: "#663c00", fontWeight: "bold" }}
              >
                Markdown Conversion:{" "}
              </Typography>
              <Typography component="span" variant="body2">
                All parsed content is converted to Markdown format, which is
                optimized for LLM processing and maintains document structure,
                headings, lists, tables, and formatting.
              </Typography>
            </Box>
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};

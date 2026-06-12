import {
  Box,
  FormControlLabel,
  FormHelperText,
  Grid,
  IconButton,
  MenuItem,
  Select,
  Slider,
  Switch,
  Tooltip,
  Typography,
} from "@mui/material";
import { assoc as _assoc, pipe as _pipe } from "lodash/fp";
import { useCallback, useContext, useMemo, useState } from "react";
import ClearIcon from "@mui/icons-material/Clear";
import ContentPasteIcon from "@mui/icons-material/ContentPaste";

import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { updateField } from "utils/fieldHelpers";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import { ColorModeContext } from "theme/material/ColorModeContext";

// Comprehensive media type options with auto-detect
const CHUNK_TEXT_MEDIA_TYPES = [
  { value: "auto", label: "Auto-detect (Recommended)", category: "Default" },
  // Code Languages - General Purpose
  { value: ".java", label: "Java (.java)" },
  { value: ".js", label: "JavaScript (.js)" },
  { value: ".ts", label: "TypeScript (.ts)" },
  { value: ".py", label: "Python (.py)" },
  { value: ".go", label: "Go (.go)" },
  { value: ".cpp", label: "C++ (.cpp)" },
  { value: ".c", label: "C (.c)" },
  { value: ".cs", label: "C# (.cs)" },
  { value: ".php", label: "PHP (.php)" },
  { value: ".rb", label: "Ruby (.rb)" },
  { value: ".swift", label: "Swift (.swift)" },
  { value: ".kt", label: "Kotlin (.kt)" },
  // Code Languages - Web & Markup
  { value: ".html", label: "HTML (.html)" },
  { value: ".css", label: "CSS (.css)" },
  { value: ".scss", label: "SCSS (.scss)" },
  { value: ".less", label: "LESS (.less)" },
  { value: ".xml", label: "XML (.xml)" },
  { value: ".yaml", label: "YAML (.yaml)" },
  { value: ".json", label: "JSON (.json)" },
  { value: ".sql", label: "SQL (.sql)" },
  // Text Formats
  { value: "text/plain", label: "Plain Text" },
  { value: "text/markdown", label: "Markdown" },
  { value: "text/html", label: "HTML" },
  // Document Types
  {
    value: "application/pdf",
    label: "PDF Document",
  },
  { value: "text/rtf", label: "Rich Text Format" },
];

const CODE_EXTENSIONS = [
  ".java",
  ".js",
  ".ts",
  ".py",
  ".go",
  ".cpp",
  ".c",
  ".cs",
  ".php",
  ".rb",
  ".swift",
  ".kt",
  ".html",
  ".css",
  ".scss",
  ".less",
  ".xml",
  ".yaml",
  ".json",
  ".sql",
];
const CODE_EXTENSIONS_FOR_CHUNK_SIZE = [
  ".java",
  ".js",
  ".ts",
  ".py",
  ".go",
  ".cpp",
  ".c",
  ".cs",
  ".php",
  ".rb",
  ".swift",
  ".kt",
];

const getChunkingStrategyDescription = (mediaType: string) => {
  if (!mediaType || mediaType === "auto") {
    return "Text will be automatically analyzed to detect the best chunking strategy based on content structure.";
  }

  if (CODE_EXTENSIONS.includes(mediaType)) {
    return "Code will be chunked with language-specific semantics, preserving function boundaries, class definitions, and logical code blocks.";
  }

  if (
    mediaType.startsWith("text/") ||
    mediaType === "application/pdf" ||
    mediaType === "text/rtf"
  ) {
    return "Text will be chunked based on natural language boundaries like paragraphs, sentences, and semantic breaks.";
  }

  return "Content will be chunked using general text chunking strategies.";
};

const estimateChunkCount = (text: string, chunkSize: number): number => {
  if (!text || !chunkSize || chunkSize <= 0) return 0;
  const textLength = text.length;
  return Math.ceil(textLength / chunkSize);
};

const getChunkSizeRecommendation = (mediaType: string): string => {
  if (CODE_EXTENSIONS_FOR_CHUNK_SIZE.includes(mediaType)) {
    return "Recommended: 1500-2000 characters for code to preserve function/class boundaries";
  }

  if (
    mediaType.startsWith("text/") ||
    mediaType === "text/plain" ||
    mediaType === "text/markdown"
  ) {
    return "Recommended: 800-1200 characters for natural language text";
  }

  if (mediaType === "application/pdf" || mediaType === "text/rtf") {
    return "Recommended: 1000-1500 characters for document content";
  }

  return "Recommended: 1024 characters (default)";
};

// Language options for syntax highlighting
const SYNTAX_LANGUAGES = [
  { value: "plaintext", label: "Plain Text" },
  { value: "javascript", label: "JavaScript" },
  { value: "typescript", label: "TypeScript" },
  { value: "python", label: "Python" },
  { value: "java", label: "Java" },
  { value: "go", label: "Go" },
  { value: "cpp", label: "C++" },
  { value: "c", label: "C" },
  { value: "csharp", label: "C#" },
  { value: "php", label: "PHP" },
  { value: "ruby", label: "Ruby" },
  { value: "swift", label: "Swift" },
  { value: "kotlin", label: "Kotlin" },
  { value: "rust", label: "Rust" },
  { value: "html", label: "HTML" },
  { value: "css", label: "CSS" },
  { value: "scss", label: "SCSS" },
  { value: "xml", label: "XML" },
  { value: "yaml", label: "YAML" },
  { value: "json", label: "JSON" },
  { value: "sql", label: "SQL" },
  { value: "markdown", label: "Markdown" },
];

export const ChunkTextTaskForm = ({ task, onChange }: TaskFormProps) => {
  const [textCharCount, setTextCharCount] = useState(0);
  const [syntaxHighlighting, setSyntaxHighlighting] = useState(false);
  const [selectedLanguage, setSelectedLanguage] = useState("plaintext");
  const { mode } = useContext(ColorModeContext);

  // Get current values from task
  const text = task.inputParameters?.text || "";
  const chunkSize = task.inputParameters?.chunkSize || 1024;
  const mediaType = task.inputParameters?.mediaType || "auto";

  // Update character count whenever text changes
  useMemo(() => {
    setTextCharCount(text.length);
  }, [text]);

  // Calculate estimated chunks
  const estimatedChunks = useMemo(
    () => estimateChunkCount(text, chunkSize),
    [text, chunkSize],
  );

  const handleTextChange = useCallback(
    (value: string) => {
      onChange(updateField("inputParameters.text", value, task));
    },
    [onChange, task],
  );

  const handleChunkSizeChange = useCallback(
    (value: number) => {
      onChange(updateField("inputParameters.chunkSize", value, task));
    },
    [onChange, task],
  );

  const handleMediaTypeChange = useCallback(
    (value: string) => {
      onChange(updateField("inputParameters.mediaType", value, task));
    },
    [onChange, task],
  );

  const handleClearText = useCallback(() => {
    handleTextChange("");
  }, [handleTextChange]);

  const handlePasteText = useCallback(async () => {
    try {
      const clipboardText = await navigator.clipboard.readText();
      handleTextChange(clipboardText);
    } catch (err) {
      console.error("Failed to read clipboard:", err);
    }
  }, [handleTextChange]);

  // Slider marks for chunk size
  const sliderMarks = [
    { value: 100, label: "100" },
    { value: 2500, label: "2.5K" },
    { value: 5000, label: "5K" },
    { value: 7500, label: "7.5K" },
    { value: 10000, label: "10K" },
  ];

  return (
    <Box padding={1} width="100%">
      {/* Text Input Section */}
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title={"Text Input"}
      >
        <Grid container spacing={3} mt={3} mb={2}>
          <Grid size={12}>
            <Box
              sx={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                mb: 2,
              }}
            >
              <FormControlLabel
                control={
                  <Switch
                    checked={syntaxHighlighting}
                    onChange={(e) => setSyntaxHighlighting(e.target.checked)}
                  />
                }
                label="Enable syntax highlighting"
              />
            </Box>
          </Grid>

          <Grid size={12}>
            {syntaxHighlighting ? (
              <Box sx={{ display: "flex", gap: 1, alignItems: "flex-start" }}>
                <Box sx={{ flexGrow: 1, position: "relative" }}>
                  <ConductorCodeBlockInput
                    key={selectedLanguage} // Force re-render when language changes
                    label="Text"
                    value={text}
                    onChange={handleTextChange}
                    language={selectedLanguage}
                    languageLabel="." // Hide default language label
                    height={400}
                    minHeight={300}
                    theme={mode === "dark" ? "vs-dark" : "light"}
                    options={{
                      minimap: { enabled: false },
                      fontSize: 13,
                      lineNumbers: "on",
                      wordWrap: "on",
                      scrollBeyondLastLine: false,
                    }}
                  />
                  {/* Language selector positioned on top border */}
                  <Box
                    sx={{
                      position: "absolute",
                      top: -17,
                      right: 0,
                      zIndex: 10,
                      background:
                        "linear-gradient(to top, #ffffff 51%, transparent 50%)",
                    }}
                  >
                    <Select
                      size="small"
                      value={selectedLanguage}
                      onChange={(e) => setSelectedLanguage(e.target.value)}
                      sx={{
                        // height: 32,
                        backgroundColor: "transparent",
                        color: "#494949",
                        fontSize: ".6rem",
                        "& .MuiSelect-select": {
                          py: 0.5,
                          px: 0.2,
                          color:
                            mode === "dark"
                              ? "rgba(255, 255, 255, 0.7)"
                              : "rgba(0, 0, 0, 0.6)",
                          fontWeight: 500,
                          textTransform: "uppercase",
                          letterSpacing: "0.5px",
                        },
                        "& .MuiOutlinedInput-notchedOutline": {
                          border: "none",
                        },
                        "&:hover .MuiSelect-select": {
                          color:
                            mode === "dark"
                              ? "rgba(255, 255, 255, 0.9)"
                              : "rgba(0, 0, 0, 0.87)",
                        },
                      }}
                    >
                      {SYNTAX_LANGUAGES.map((lang) => (
                        <MenuItem
                          key={lang.value}
                          value={lang.value}
                          sx={{ fontSize: "0.875rem" }}
                        >
                          {lang.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </Box>
                </Box>
                <Box
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    gap: 1,
                    pt: 4,
                  }}
                >
                  <Tooltip title="Paste from clipboard" placement="left">
                    <IconButton
                      size="small"
                      onClick={handlePasteText}
                      sx={{
                        backgroundColor: "action.hover",
                        "&:hover": {
                          backgroundColor: "action.selected",
                        },
                      }}
                    >
                      <ContentPasteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Clear text" placement="left">
                    <span>
                      <IconButton
                        size="small"
                        onClick={handleClearText}
                        disabled={!text}
                        sx={{
                          backgroundColor: "action.hover",
                          "&:hover": {
                            backgroundColor: "action.selected",
                          },
                        }}
                      >
                        <ClearIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </Box>
              </Box>
            ) : (
              <Box sx={{ display: "flex", gap: 1, alignItems: "flex-start" }}>
                <Box sx={{ flexGrow: 1 }}>
                  <ConductorInput
                    label="Text"
                    name="text"
                    value={text}
                    onTextInputChange={handleTextChange}
                    multiline
                    rows={12}
                    fullWidth
                    placeholder="Enter or paste text to be chunked..."
                    helperText={`${textCharCount.toLocaleString()} characters`}
                    inputProps={{
                      style: {
                        fontFamily: "monospace",
                        fontSize: "13px",
                        resize: "vertical",
                      },
                    }}
                  />
                </Box>
                <Box
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    gap: 1,
                    pt: 4,
                  }}
                >
                  <Tooltip title="Paste from clipboard" placement="left">
                    <IconButton
                      size="small"
                      onClick={handlePasteText}
                      sx={{
                        backgroundColor: "action.hover",
                        "&:hover": {
                          backgroundColor: "action.selected",
                        },
                      }}
                    >
                      <ContentPasteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Clear text" placement="left">
                    <span>
                      <IconButton
                        size="small"
                        onClick={handleClearText}
                        disabled={!text}
                        sx={{
                          backgroundColor: "action.hover",
                          "&:hover": {
                            backgroundColor: "action.selected",
                          },
                        }}
                      >
                        <ClearIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </Box>
              </Box>
            )}
          </Grid>
        </Grid>
      </TaskFormSection>

      {/* Chunk Size Section */}
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title={"Chunk Size"}
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
                    if (!isNaN(numValue) && numValue > 0 && numValue <= 10000) {
                      handleChunkSizeChange(numValue);
                    }
                  }}
                  fullWidth
                  error={chunkSize < 100 || chunkSize > 10000}
                  helperText="Enter a value between 100 and 10000"
                  inputProps={{ min: 100, max: 10000 }}
                />
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <Box sx={{ mt: -0.5 }}>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    gutterBottom
                  >
                    Estimated chunks: <strong>{estimatedChunks}</strong>
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Characters per chunk: <strong>{chunkSize}</strong>
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
                min={100}
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
        </Grid>
      </TaskFormSection>

      {/* Media Type Section */}
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title={"Media Type"}
      >
        <Grid container spacing={3} mt={3} mb={4}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              openOnFocus
              onChange={(value) => handleMediaTypeChange(value || "auto")}
              value={mediaType}
              otherOptions={CHUNK_TEXT_MEDIA_TYPES.map((opt) => opt.value)}
              label="Media Type"
              helperText="Select a media type or use auto-detect to automatically determine the best chunking strategy"
              getOptionLabel={(option) =>
                CHUNK_TEXT_MEDIA_TYPES.find((opt) => opt.value === option)
                  ?.label ?? option.toString()
              }
              renderOption={(props, option) => (
                <Box component="li" {...props}>
                  <Typography>
                    {CHUNK_TEXT_MEDIA_TYPES.find((opt) => opt.value === option)
                      ?.label ?? option}
                  </Typography>
                </Box>
              )}
            />
          </Grid>

          <Grid size={12}>
            <Box>
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
                <strong>Chunking Strategy:</strong>{" "}
                {getChunkingStrategyDescription(mediaType)}
              </Box>
            </Box>
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={task} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};

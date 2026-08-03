import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Stack,
  Typography,
} from "@mui/material";
import { ReactJson } from "components";

type MemoryResult = {
  key?: string;
  value_preview?: string;
  scope?: string;
  relevance_score?: number;
  tags?: string[];
  good_count?: number;
  bad_count?: number;
  feedback?: string;
};

type MemorySearchResponse = {
  query?: string;
  results?: MemoryResult[];
  total?: number;
};

const object = (value: unknown): Record<string, unknown> | null =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;

export const memorySearchResponse = (
  output: unknown,
): MemorySearchResponse | null => {
  const root = object(output);
  const content = root?.content;
  if (!Array.isArray(content)) return null;
  for (const item of content) {
    const block = object(item);
    const parsed = object(block?.parsed);
    if (Array.isArray(parsed?.results)) return parsed as MemorySearchResponse;
    if (typeof block?.text !== "string") continue;
    try {
      const decoded = JSON.parse(block.text);
      if (Array.isArray(decoded?.results))
        return decoded as MemorySearchResponse;
    } catch {
      // Not a JSON MCP text block; render the ordinary task output instead.
    }
  }
  return null;
};

export const McpMemorySearchOutput = ({
  output,
  response,
  workflowName,
}: {
  output: Record<string, unknown>;
  response: MemorySearchResponse;
  workflowName: string;
}) => (
  <Box sx={{ p: 3, overflowY: "auto", maxHeight: "calc(100vh - 280px)" }}>
    <Typography variant="subtitle2">Memory search</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
      {response.total ?? response.results?.length ?? 0} result(s) for “
      {response.query || "query"}”
    </Typography>
    <Stack spacing={1.5}>
      {response.results?.map((result, index) => (
        <Box
          key={result.key || index}
          sx={{ border: 1, borderColor: "divider", borderRadius: 1, p: 1.5 }}
        >
          <Typography variant="subtitle2" sx={{ overflowWrap: "anywhere" }}>
            {result.key || "Memory result"}
          </Typography>
          <Stack
            direction="row"
            spacing={0.75}
            flexWrap="wrap"
            useFlexGap
            sx={{ my: 1 }}
          >
            {result.scope && (
              <Chip size="small" label={`Scope: ${result.scope}`} />
            )}
            {typeof result.relevance_score === "number" && (
              <Chip
                size="small"
                label={`Relevance: ${result.relevance_score.toFixed(2)}`}
              />
            )}
            {typeof result.good_count === "number" && (
              <Chip
                size="small"
                color="success"
                label={`Helpful: ${result.good_count}`}
              />
            )}
            {typeof result.bad_count === "number" && (
              <Chip
                size="small"
                color="error"
                label={`Not helpful: ${result.bad_count}`}
              />
            )}
          </Stack>
          {result.value_preview && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ whiteSpace: "pre-wrap" }}
            >
              {result.value_preview}
            </Typography>
          )}
          {result.feedback && (
            <Typography variant="caption">{result.feedback}</Typography>
          )}
        </Box>
      ))}
      {response.results?.length === 0 && (
        <Typography color="text.secondary">
          No memories matched this query.
        </Typography>
      )}
    </Stack>
    <Accordion
      disableGutters
      elevation={0}
      sx={{ mt: 2, border: 1, borderColor: "divider" }}
    >
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Typography variant="body2">Raw response</Typography>
      </AccordionSummary>
      <AccordionDetails sx={{ p: 0 }}>
        <ReactJson
          src={output}
          title="MCP response"
          overflowY="auto"
          overflowX="hidden"
          workflowName={workflowName}
          editorHeight="320px"
        />
      </AccordionDetails>
    </Accordion>
  </Box>
);

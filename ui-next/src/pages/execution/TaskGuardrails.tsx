import {
  Box,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { NavLink } from "components";
import { ExecutionTask } from "types/Execution";
import { useFetch, useWorkflowSearch } from "utils/query";

interface TaskGuardrailsProps {
  taskResult: ExecutionTask;
}

const promptOf = (obj: any): string | undefined =>
  obj && obj.prompt != null ? String(obj.prompt) : undefined;

const cellSx = {
  maxWidth: 320,
  verticalAlign: "top" as const,
};

// Long before/after text (e.g. a full LLM response) scrolls inside the cell
// instead of ballooning the row.
const textBoxSx = {
  maxHeight: 180,
  overflowY: "auto" as const,
  whiteSpace: "pre-wrap" as const,
  wordBreak: "break-word" as const,
  fontSize: "0.8em",
};

/**
 * One guardrail run. The workflow-search summary serializes input/output as
 * non-JSON strings, so we fetch the child workflow to read its real
 * input.prompt (before) and output.prompt (after).
 */
function GuardrailRow({
  workflowId,
  fallbackType,
}: {
  workflowId: string;
  fallbackType?: string;
}) {
  const { data: wf } = useFetch<any>(`/workflow/${workflowId}`, {
    enabled: !!workflowId,
  });
  const name = wf?.workflowName ?? fallbackType ?? workflowId;
  const status = wf?.status;
  const before = promptOf(wf?.input);
  const after = promptOf(wf?.output);
  return (
    <TableRow>
      <TableCell>
        <NavLink path={`/execution/${workflowId}`}>{name}</NavLink>
      </TableCell>
      <TableCell sx={{ verticalAlign: "top" }}>{status ?? "—"}</TableCell>
      <TableCell sx={cellSx}>
        <Box sx={textBoxSx}>{before ?? "—"}</Box>
      </TableCell>
      <TableCell sx={cellSx}>
        <Box sx={textBoxSx}>{after ?? "—"}</Box>
      </TableCell>
    </TableRow>
  );
}

export default function TaskGuardrails({ taskResult }: TaskGuardrailsProps) {
  const taskId = taskResult?.taskId;
  const { data, isLoading, error } = useWorkflowSearch<any>(
    {
      rowsPerPage: 100,
      page: 1,
      sort: "startTime:ASC",
      query: `correlationId="guardrail:${taskId}"`,
    },
    { enabled: !!taskId },
  );

  const results: any[] = data?.results ?? [];

  return (
    <Paper variant="outlined" sx={{ margin: 3 }}>
      <Box p={2}>
        <Typography variant="subtitle1" gutterBottom>
          Guardrail executions
        </Typography>
        <Typography variant="body2" sx={{ opacity: 0.6, mb: 2 }}>
          Each guardrail runs as a linked sub-workflow (correlationId{" "}
          <code>guardrail:{taskId}</code>). Input = before scrubbing, output =
          after.
        </Typography>

        {isLoading && <CircularProgress size={20} />}
        {error && (
          <Typography color="error" variant="body2">
            Failed to load guardrail executions.
          </Typography>
        )}
        {!isLoading && !error && results.length === 0 && (
          <Typography variant="body2" sx={{ opacity: 0.6 }}>
            No guardrail executions for this task.
          </Typography>
        )}

        {results.length > 0 && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Guardrail workflow</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Before</TableCell>
                <TableCell>After</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {results.map((r) => (
                <GuardrailRow
                  key={r.workflowId}
                  workflowId={r.workflowId}
                  fallbackType={r.workflowType ?? r.workflowName}
                />
              ))}
            </TableBody>
          </Table>
        )}
      </Box>
    </Paper>
  );
}

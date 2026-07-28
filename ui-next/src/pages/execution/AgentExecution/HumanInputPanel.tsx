/**
 * HumanInputPanel — shown when an agent execution is PAUSED awaiting human input.
 *
 * Handles two cases:
 *   1. Tool approval  (@tool(approval_required=True)) — approve/reject buttons
 *   2. MANUAL strategy agent selection — dropdown + confirm button
 *
 * Talks to the embedded AgentSpan REST API (conductor-agentspan module,
 * gated by conductor.integrations.ai.enabled) — /api/agent/:id/status and
 * /api/agent/:id/respond.
 */

import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  MenuItem,
  Select,
  TextField,
  Typography,
} from "@mui/material";
import { CheckCircle, XCircle, CaretDown } from "@phosphor-icons/react";

interface PendingTool {
  taskRefName?: string;
  tool_name?: string;
  parameters?: Record<string, unknown>;
  response_schema?: Record<string, unknown>;
}

interface AgentStatusResponse {
  isWaiting?: boolean;
  pendingTool?: PendingTool;
}

interface HumanInputPanelProps {
  executionId: string;
  /** List of sub-agent names for MANUAL strategy selection */
  subAgentNames?: string[];
}

function isManualSelection(pendingTool: PendingTool | undefined): boolean {
  if (!pendingTool?.tool_name) return false;
  const name = pendingTool.tool_name.toLowerCase();
  return name.includes("process_selection") || name.includes("selection");
}

function usePendingTool(executionId: string): {
  pendingTool: PendingTool | undefined;
  loading: boolean;
} {
  const [pendingTool, setPendingTool] = useState<PendingTool | undefined>(
    undefined,
  );
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const fetchStatus = async () => {
      try {
        const res = await fetch(`/api/agent/${executionId}/status`);
        if (!res.ok || cancelled) return;
        const data: AgentStatusResponse = await res.json();
        if (!cancelled) {
          setPendingTool(data.isWaiting ? data.pendingTool : undefined);
        }
      } catch {
        // ignore — panel shows a fallback when tool is undefined
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchStatus();
    return () => {
      cancelled = true;
    };
  }, [executionId]);

  return { pendingTool, loading };
}

async function callRespond(
  executionId: string,
  body: Record<string, unknown>,
): Promise<void> {
  const res = await fetch(`/api/agent/${executionId}/respond`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`respond failed (${res.status}): ${text}`);
  }
}

// ─── Approval form ────────────────────────────────────────────────────────────

function ApprovalForm({
  executionId,
  toolName,
  parameters,
  onDone,
}: {
  executionId: string;
  toolName?: string;
  parameters?: Record<string, unknown>;
  onDone: () => void;
}) {
  const [rejectReason, setRejectReason] = useState("");
  const [showRejectInput, setShowRejectInput] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const approve = useCallback(async () => {
    setSubmitting(true);
    setError(null);
    try {
      await callRespond(executionId, { approved: true });
      onDone();
    } catch (err) {
      setError(String(err));
    } finally {
      setSubmitting(false);
    }
  }, [executionId, onDone]);

  const reject = useCallback(async () => {
    setSubmitting(true);
    setError(null);
    try {
      await callRespond(executionId, {
        approved: false,
        reason: rejectReason || undefined,
      });
      onDone();
    } catch (err) {
      setError(String(err));
    } finally {
      setSubmitting(false);
    }
  }, [executionId, rejectReason, onDone]);

  return (
    <Box>
      {/* Tool name + parameters */}
      <Box sx={{ mb: 1.5 }}>
        <Typography sx={{ fontWeight: 700, fontSize: "0.8rem", mb: 0.25 }}>
          {toolName ?? "Tool call"}
        </Typography>
        {parameters && Object.keys(parameters).length > 0 && (
          <Box
            component="pre"
            sx={{
              m: 0,
              p: 1,
              backgroundColor: "grey.50",
              border: "1px solid",
              borderColor: "divider",
              borderRadius: 0.5,
              fontSize: "0.72rem",
              fontFamily: "monospace",
              whiteSpace: "pre-wrap",
              wordBreak: "break-word",
              maxHeight: 160,
              overflowY: "auto",
            }}
          >
            {JSON.stringify(parameters, null, 2)}
          </Box>
        )}
      </Box>

      {/* Reject reason input (conditional) */}
      {showRejectInput && (
        <TextField
          size="small"
          fullWidth
          placeholder="Rejection reason (optional)"
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          sx={{ mb: 1, fontSize: "0.8rem" }}
          inputProps={{ style: { fontSize: "0.8rem" } }}
        />
      )}

      {/* Buttons */}
      <Box sx={{ display: "flex", gap: 1 }}>
        <Button
          variant="contained"
          size="small"
          color="success"
          startIcon={
            submitting ? (
              <CircularProgress size={12} color="inherit" />
            ) : (
              <CheckCircle size={14} />
            )
          }
          onClick={approve}
          disabled={submitting}
          sx={{ fontSize: "0.75rem", textTransform: "none" }}
        >
          Approve
        </Button>
        {showRejectInput ? (
          <Button
            variant="contained"
            size="small"
            color="error"
            startIcon={
              submitting ? (
                <CircularProgress size={12} color="inherit" />
              ) : (
                <XCircle size={14} />
              )
            }
            onClick={reject}
            disabled={submitting}
            sx={{ fontSize: "0.75rem", textTransform: "none" }}
          >
            Confirm Reject
          </Button>
        ) : (
          <Button
            variant="outlined"
            size="small"
            color="error"
            startIcon={<XCircle size={14} />}
            onClick={() => setShowRejectInput(true)}
            disabled={submitting}
            sx={{ fontSize: "0.75rem", textTransform: "none" }}
          >
            Reject
          </Button>
        )}
      </Box>

      {error && (
        <Alert severity="error" sx={{ mt: 1, fontSize: "0.75rem", py: 0.25 }}>
          {error}
        </Alert>
      )}
    </Box>
  );
}

// ─── Manual agent selection form ──────────────────────────────────────────────

function AgentSelectionForm({
  executionId,
  subAgentNames,
  onDone,
}: {
  executionId: string;
  subAgentNames: string[];
  onDone: () => void;
}) {
  const [selected, setSelected] = useState<string>(subAgentNames[0] ?? "0");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const confirm = useCallback(async () => {
    setSubmitting(true);
    setError(null);
    try {
      await callRespond(executionId, { selected });
      onDone();
    } catch (err) {
      setError(String(err));
    } finally {
      setSubmitting(false);
    }
  }, [executionId, selected, onDone]);

  return (
    <Box>
      <Typography sx={{ fontSize: "0.8rem", mb: 1, color: "text.secondary" }}>
        Select the next agent to run:
      </Typography>
      <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
        <Select
          size="small"
          value={selected}
          onChange={(e) => setSelected(e.target.value)}
          sx={{ fontSize: "0.8rem", minWidth: 180 }}
          IconComponent={CaretDown}
        >
          {subAgentNames.map((name) => (
            <MenuItem key={name} value={name} sx={{ fontSize: "0.8rem" }}>
              {name}
            </MenuItem>
          ))}
        </Select>
        <Button
          variant="contained"
          size="small"
          color="primary"
          onClick={confirm}
          disabled={submitting || !selected}
          startIcon={
            submitting ? (
              <CircularProgress size={12} color="inherit" />
            ) : undefined
          }
          sx={{ fontSize: "0.75rem", textTransform: "none" }}
        >
          Run selected agent
        </Button>
      </Box>
      {error && (
        <Alert severity="error" sx={{ mt: 1, fontSize: "0.75rem", py: 0.25 }}>
          {error}
        </Alert>
      )}
    </Box>
  );
}

// ─── Panel wrapper ────────────────────────────────────────────────────────────

export function HumanInputPanel({
  executionId,
  subAgentNames = [],
}: HumanInputPanelProps) {
  const { pendingTool, loading } = usePendingTool(executionId);
  const [dismissed, setDismissed] = useState(false);

  if (dismissed) return null;

  return (
    <Box
      sx={{
        px: 2,
        py: 1.5,
        borderBottom: "2px solid",
        borderColor: "#f59e0b",
        backgroundColor: "#fffbeb",
        flexShrink: 0,
      }}
    >
      <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1.5 }}>
        {/* Status indicator dot */}
        <Box
          sx={{
            width: 8,
            height: 8,
            borderRadius: "50%",
            backgroundColor: "#f59e0b",
            flexShrink: 0,
            mt: 0.75,
            animation: "pulse 1.5s infinite",
            "@keyframes pulse": {
              "0%, 100%": { opacity: 1 },
              "50%": { opacity: 0.4 },
            },
          }}
        />

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography
            sx={{
              fontWeight: 700,
              fontSize: "0.78rem",
              color: "#92400e",
              mb: 0.75,
              textTransform: "uppercase",
              letterSpacing: "0.04em",
            }}
          >
            Waiting for your input
          </Typography>

          {loading ? (
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <CircularProgress size={14} sx={{ color: "#f59e0b" }} />
              <Typography sx={{ fontSize: "0.75rem", color: "text.secondary" }}>
                Loading pending action…
              </Typography>
            </Box>
          ) : isManualSelection(pendingTool) ? (
            <AgentSelectionForm
              executionId={executionId}
              subAgentNames={subAgentNames.length > 0 ? subAgentNames : ["0"]}
              onDone={() => setDismissed(true)}
            />
          ) : (
            <ApprovalForm
              executionId={executionId}
              toolName={pendingTool?.tool_name}
              parameters={pendingTool?.parameters}
              onDone={() => setDismissed(true)}
            />
          )}
        </Box>
      </Box>
    </Box>
  );
}

export default HumanInputPanel;

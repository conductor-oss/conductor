import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from "@mui/material";
import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "react-query";
import { useAuthHeaders } from "utils/query";

export type AgentFeedbackRating = "positive" | "negative";

export interface AgentFeedbackState {
  enabled: boolean;
  rating?: AgentFeedbackRating | null;
  submittedAt?: string | null;
  reason?: string | null;
}

interface AgentExecutionMemoryState {
  summary?: string | null;
  captureWorkflowId?: string | null;
  captureWorkflowStatus?: string | null;
}

interface AgentFeedbackControlsProps {
  executionId: string;
  executionStatus?: string;
}

export const AgentFeedbackControls = ({
  executionId,
  executionStatus,
}: AgentFeedbackControlsProps) => {
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();
  const queryClient = useQueryClient();
  const [submitError, setSubmitError] = useState(false);
  const [pendingRating, setPendingRating] =
    useState<AgentFeedbackRating | null>(null);
  const [reason, setReason] = useState("");
  // A running execution is ineligible. Include its status so the terminal transition performs a
  // fresh eligibility read without requiring the user to leave and reopen the execution page.
  const queryKey = [
    "agent-feedback",
    fetchContext.stack,
    executionId,
    executionStatus,
  ];
  const path = `agent/executions/${encodeURIComponent(executionId)}/feedback`;
  const memoryPath = `${path}/memory`;

  const feedback = useQuery<AgentFeedbackState>(
    queryKey,
    () =>
      fetchWithContext(path, fetchContext, {
        headers: authHeaders,
      }),
    {
      retry: false,
    },
  );

  const submit = useMutation<
    AgentFeedbackState,
    unknown,
    { rating: AgentFeedbackRating; reason: string }
  >(
    ({ rating, reason: submittedReason }) =>
      fetchWithContext(path, fetchContext, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify({ rating, reason: submittedReason }),
      }),
    {
      onSuccess: (state) => {
        setSubmitError(false);
        queryClient.setQueryData(queryKey, state);
        setPendingRating(null);
        setReason("");
      },
      onError: () => setSubmitError(true),
    },
  );

  const memory = useQuery<AgentExecutionMemoryState>(
    ["agent-feedback-memory", fetchContext.stack, executionId],
    () =>
      fetchWithContext(memoryPath, fetchContext, {
        headers: authHeaders,
      }),
    {
      enabled: pendingRating !== null,
      retry: false,
    },
  );

  // A missing endpoint on an older server and an authoritative disabled response both hide the
  // controls, allowing the UI to roll out independently from the OCG feedback wire contract.
  if (feedback.isLoading || feedback.isError || !feedback.data?.enabled) {
    return null;
  }

  const selected = feedback.data.rating;
  const openFeedbackModal = (rating: AgentFeedbackRating) => {
    setSubmitError(false);
    setPendingRating(rating);
    setReason("");
  };
  const closeFeedbackModal = () => {
    if (submit.isLoading) return;
    setPendingRating(null);
    setReason("");
    setSubmitError(false);
  };
  const trimmedReason = reason.trim();

  return (
    <Box display="flex" flexDirection="column" gap={0.5}>
      <Box display="flex" alignItems="center" gap={1}>
        <Typography variant="caption">
          Was this agent result helpful?
        </Typography>
        <Button
          size="small"
          variant={selected === "positive" ? "contained" : "outlined"}
          disabled={submit.isLoading}
          onClick={() => openFeedbackModal("positive")}
        >
          Helpful
        </Button>
        <Button
          size="small"
          variant={selected === "negative" ? "contained" : "outlined"}
          disabled={submit.isLoading}
          onClick={() => openFeedbackModal("negative")}
        >
          Not helpful
        </Button>
      </Box>
      <Dialog
        open={pendingRating !== null}
        onClose={closeFeedbackModal}
        fullWidth
        maxWidth="sm"
        aria-labelledby="agent-feedback-title"
      >
        <DialogTitle id="agent-feedback-title">Share feedback</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 2 }}>
            You marked this result as{" "}
            <strong>
              {pendingRating === "positive" ? "helpful" : "not helpful"}
            </strong>
            . Tell us why.
          </Typography>
          <TextField
            fullWidth
            multiline
            minRows={8}
            maxRows={8}
            label="Execution memory"
            value={
              memory.isLoading
                ? "Loading execution memory…"
                : memory.data?.summary ||
                  "No execution memory is available yet."
            }
            InputProps={{
              readOnly: true,
              sx: {
                backgroundColor: "action.disabledBackground",
                color: "text.secondary",
                "& textarea": { overflowY: "auto" },
              },
            }}
            sx={{ mb: 2 }}
          />
          {memory.data?.captureWorkflowId && (
            <Typography variant="caption" display="block" sx={{ mb: 2 }}>
              Memory capture: {memory.data.captureWorkflowStatus || "RUNNING"} —{" "}
              <a href={`/execution/${memory.data.captureWorkflowId}`}>
                View capture workflow
              </a>
            </Typography>
          )}
          <TextField
            autoFocus
            required
            fullWidth
            multiline
            minRows={4}
            label="Reason"
            placeholder="Describe what worked or what could be improved"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            disabled={submit.isLoading}
            inputProps={{ maxLength: 2000 }}
            helperText={`${reason.length}/2000 characters`}
          />
          {submitError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              Feedback could not be saved. Please try again.
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeFeedbackModal} disabled={submit.isLoading}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={
              pendingRating === null ||
              trimmedReason.length === 0 ||
              submit.isLoading
            }
            onClick={() => {
              if (pendingRating === null || trimmedReason.length === 0) return;
              setSubmitError(false);
              submit.mutate({ rating: pendingRating, reason: trimmedReason });
            }}
          >
            Submit feedback
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

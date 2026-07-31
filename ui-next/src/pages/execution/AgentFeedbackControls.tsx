import { Alert, Box, Button, Typography } from "@mui/material";
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

interface AgentFeedbackControlsProps {
  executionId: string;
}

export const AgentFeedbackControls = ({
  executionId,
}: AgentFeedbackControlsProps) => {
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();
  const queryClient = useQueryClient();
  const [submitError, setSubmitError] = useState(false);
  const queryKey = ["agent-feedback", fetchContext.stack, executionId];
  const path = `agent/executions/${encodeURIComponent(executionId)}/feedback`;

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

  const submit = useMutation<AgentFeedbackState, unknown, AgentFeedbackRating>(
    (rating) =>
      fetchWithContext(path, fetchContext, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify({ rating }),
      }),
    {
      onSuccess: (state) => {
        setSubmitError(false);
        queryClient.setQueryData(queryKey, state);
      },
      onError: () => setSubmitError(true),
    },
  );

  // A missing endpoint on an older server and an authoritative disabled response both hide the
  // controls, allowing the UI to roll out independently from the OCG feedback wire contract.
  if (feedback.isLoading || feedback.isError || !feedback.data?.enabled) {
    return null;
  }

  const selected = feedback.data.rating;
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
          onClick={() => {
            setSubmitError(false);
            submit.mutate("positive");
          }}
        >
          Helpful
        </Button>
        <Button
          size="small"
          variant={selected === "negative" ? "contained" : "outlined"}
          disabled={submit.isLoading}
          onClick={() => {
            setSubmitError(false);
            submit.mutate("negative");
          }}
        >
          Not helpful
        </Button>
      </Box>
      {submitError && (
        <Alert severity="error" sx={{ py: 0 }}>
          Feedback could not be saved. Please try again.
        </Alert>
      )}
    </Box>
  );
};

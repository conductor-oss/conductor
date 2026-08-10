import { FunctionComponent } from "react";
import { ValidationError } from "./state/types";
import { Box, Typography } from "@mui/material";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import { AccordionErrorSummary } from "./AccordionErrorSummary";
import { WarningCircle } from "@phosphor-icons/react";

const OUTPUT_PARAMETER_REFERENCE = "outputParameters";

// Helper component to color words wrapped in double asterisks
const ColoredAsteriskText: FunctionComponent<{ text: string }> = ({ text }) => {
  // Regex to match **word**
  const regex = /\*\*(.+?)\*\*/g;
  const parts = [];
  let lastIndex = 0;
  let match;
  let key = 0;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index));
    }
    parts.push(
      <span key={key++} style={{ color: "cyan", fontWeight: 600 }}>
        {match[1]}
      </span>,
    );
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }
  return <>{parts}</>;
};

const WorkflowSingleError: FunctionComponent<ValidationError> = ({
  hint,
  message,
  onClickReference,
}) => (
  <Box
    sx={{
      p: 2,
      mb: 1,
      borderRadius: 1,
      backgroundColor: "rgba(255, 255, 255, 0.05)",
      border: "1px solid rgba(255, 255, 255, 0.1)",
      transition: "all 0.2s ease-in-out",
      "&:hover": {
        backgroundColor: "rgba(255, 255, 255, 0.08)",
        borderColor: "rgba(255, 255, 255, 0.2)",
      },
    }}
  >
    <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
      <WarningCircle size={16} color="#ff6b6b" style={{ marginRight: 8 }} />
      <Typography variant="body2" sx={{ color: "#e0e0e0", fontWeight: 500 }}>
        Workflow Error:
      </Typography>
    </Box>

    <Box
      sx={{
        display: "flex",
        alignItems: "flex-start",
        cursor: "pointer",
        p: 1,
        borderRadius: 0.5,
        backgroundColor: "rgba(255, 255, 255, 0.03)",
        transition: "background-color 0.2s ease-in-out",
        "&:hover": {
          backgroundColor: "rgba(255, 255, 255, 0.08)",
        },
      }}
      data-testid="workflow-json-error-message"
      onClick={() => onClickReference?.(OUTPUT_PARAMETER_REFERENCE)}
    >
      <Typography
        variant="body2"
        sx={{ color: "#b0b0b0", fontWeight: 500, mr: 1 }}
      >
        Message:
      </Typography>
      <Typography variant="body2" sx={{ color: "#ffffff", flex: 1 }}>
        <ColoredAsteriskText text={message} />
      </Typography>
    </Box>

    {hint && (
      <Box
        sx={{
          display: "flex",
          alignItems: "flex-start",
          mt: 1,
          p: 1,
          borderRadius: 0.5,
          backgroundColor: "rgba(255, 193, 7, 0.1)",
          border: "1px solid rgba(255, 193, 7, 0.2)",
        }}
      >
        <Typography
          variant="body2"
          sx={{ color: "#ffc107", fontWeight: 500, mr: 1 }}
        >
          Hint:
        </Typography>
        <Typography variant="body2" sx={{ color: "#ffffff", flex: 1 }}>
          {hint}
        </Typography>
      </Box>
    )}
  </Box>
);

interface WorkflowErrorsDisplayerProps {
  workflowErrors: ValidationError[];
  expanded: boolean;
  onToggleExpand: () => void;
  title?: string;
  onClickReference?: (data: string) => void;
}

export const WorkflowErrorsDisplayer: FunctionComponent<
  WorkflowErrorsDisplayerProps
> = ({
  workflowErrors,
  expanded,
  onToggleExpand,
  title = "Workflow errors",
  onClickReference,
}) => {
  const totalErrorCount = workflowErrors?.length || 0;

  return (
    <Accordion
      sx={{
        background: "none",
        boxShadow: "none",
        color: "white",
        "&:not(:last-child)": {
          borderBottom: 0,
        },
        "&:before": {
          display: "none",
        },
        "&.Mui-expanded": {
          marginBottom: 0,
          marginTop: 0,
        },
      }}
      className="workflow-error-displayer"
      expanded={expanded}
      onChange={onToggleExpand}
    >
      <AccordionErrorSummary
        title={title}
        expanded={expanded}
        count={totalErrorCount}
      />
      <AccordionDetails
        sx={{
          color: "#cccccc",
          pt: 2,
          pb: 1,
        }}
      >
        <Box>
          {workflowErrors.map((validationError) => (
            <WorkflowSingleError
              key={`${validationError.id}-${validationError.message?.slice(0, 20)}`}
              onClickReference={onClickReference}
              {...validationError}
            />
          ))}
        </Box>
      </AccordionDetails>
    </Accordion>
  );
};

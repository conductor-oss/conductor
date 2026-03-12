import { FunctionComponent, useReducer } from "react";
import _nth from "lodash/nth";
import _isArray from "lodash/isArray";
import { TaskErrors, ValidationError } from "./state/types";
import { Box, Chip, Typography } from "@mui/material";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import Collapse from "@mui/material/Collapse";
import ListItemButton from "@mui/material/ListItemButton";
import { AccordionErrorSummary } from "./AccordionErrorSummary";
import { get } from "lodash";
import { CaretRight, CaretDown, WarningCircle } from "@phosphor-icons/react";

const TaskSingleError: FunctionComponent<ValidationError> = ({
  id,
  hint,
  message,
  taskReferenceName,
  onClickReference,
  taskError,
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
        Task reference:
      </Typography>
      <Chip
        label={taskReferenceName}
        size="small"
        sx={{
          ml: 1,
          backgroundColor: "#2196f3",
          color: "white",
          fontSize: "0.75rem",
          height: 20,
        }}
      />
      <Chip
        label={id}
        size="small"
        sx={{
          ml: 1,
          backgroundColor: "#00bcd4",
          color: "white",
          fontSize: "0.75rem",
          height: 20,
        }}
      />
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
      onClick={() => onClickReference?.(errorRefExtractor(message, taskError))}
    >
      <Typography
        variant="body2"
        sx={{ color: "#b0b0b0", fontWeight: 500, mr: 1 }}
      >
        Message:
      </Typography>
      <Typography variant="body2" sx={{ color: "#ffffff", flex: 1 }}>
        {message}
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

const errorRefExtractor = (string: string, taskError: TaskErrors) => {
  let key = _nth(string.split("'"), 1);
  if (key !== undefined) {
    const value = get(taskError?.task?.inputParameters, key);
    if (_isArray(value)) {
      return `"${key}"`;
    }
    if (key.includes(".")) {
      key = key.substring(key.lastIndexOf(".") + 1);
    }
    if (value) {
      return `"${key}": "${value}"`;
    }
    return key;
  }
  return "";
};

interface TaskGroupedErrorsProps {
  taskError: TaskErrors;
  onClickReference?: (data: string) => void;
  title: string;
}

const TaskGroupedErrors: FunctionComponent<TaskGroupedErrorsProps> = ({
  taskError,
  onClickReference,
  title,
}) => {
  const [isExpanded, toggleExpand] = useReducer((s) => !s, false);

  function returnTaskReferenceName() {
    if (title === "Unreachable tasks") {
      const value = _nth(taskError?.errors, 0);
      if (value !== undefined) {
        return value.taskReferenceName;
      }
      return "unknown_task";
    }
    return taskError.task.taskReferenceName;
  }

  const taskName = returnTaskReferenceName();
  const errorCount = taskError.errors.length;

  return (
    <Box sx={{ mb: 1 }}>
      <ListItemButton
        onClick={toggleExpand}
        sx={{
          borderRadius: 1,
          mb: 1,
          backgroundColor: "rgba(255, 255, 255, 0.05)",
          border: "1px solid rgba(255, 255, 255, 0.1)",
          transition: "all 0.2s ease-in-out",
          "&:hover": {
            backgroundColor: "rgba(255, 255, 255, 0.1)",
            borderColor: "rgba(255, 255, 255, 0.2)",
            transform: "translateY(-1px)",
            boxShadow: "0 2px 8px rgba(0, 0, 0, 0.2)",
          },
          "&:active": {
            transform: "translateY(0)",
          },
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", width: "100%" }}>
          <Box sx={{ mr: 2, display: "flex", alignItems: "center" }}>
            {isExpanded ? (
              <CaretDown size={16} color="#ffffff" />
            ) : (
              <CaretRight size={16} color="#ffffff" />
            )}
          </Box>

          <Box sx={{ display: "flex", alignItems: "center", flex: 1 }}>
            <Typography
              variant="subtitle2"
              sx={{
                color: "#ffffff",
                fontWeight: 600,
                mr: 2,
              }}
            >
              {taskName}
            </Typography>

            <Chip
              label={`${errorCount} issue${errorCount > 1 ? "s" : ""}`}
              size="small"
              sx={{
                backgroundColor: "#ff9800",
                color: "white",
                fontSize: "0.7rem",
                height: 20,
                fontWeight: 500,
              }}
            />
          </Box>
        </Box>
      </ListItemButton>

      <Collapse in={isExpanded} timeout="auto">
        <Box
          sx={{
            ml: 3,
            pl: 2,
            borderLeft: "2px solid rgba(255, 255, 255, 0.1)",
            backgroundColor: "rgba(255, 255, 255, 0.02)",
            borderRadius: "0 4px 4px 0",
          }}
        >
          {taskError.errors.map((tr) => (
            <Box key={`${tr.id}-${tr.taskReferenceName}`} sx={{ mb: 1, mt: 1 }}>
              <TaskSingleError
                {...tr}
                onClickReference={onClickReference}
                taskError={taskError}
              />
            </Box>
          ))}
        </Box>
      </Collapse>
    </Box>
  );
};

interface TaskErrorsDisplayerProps {
  taskErrors: TaskErrors[];
  expanded: boolean;
  onToggleExpand: () => void;
  title?: string;
  onClickReference?: (data: string) => void;
}

export const TaskErrorsDisplayer: FunctionComponent<
  TaskErrorsDisplayerProps
> = ({
  taskErrors,
  expanded,
  onToggleExpand,
  title = "Task Errors",
  onClickReference,
}) => {
  const totalErrorCount = taskErrors?.reduce(
    (sum, taskError) => sum + taskError?.errors?.length,
    0,
  );

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
          pb: 0,
        }}
      >
        <Box>
          {taskErrors.map((taskError) => (
            <TaskGroupedErrors
              title={title}
              taskError={taskError}
              key={`${taskError.task.taskReferenceName}-${taskError.errors.length}`}
              onClickReference={onClickReference}
            />
          ))}
        </Box>
      </AccordionDetails>
    </Accordion>
  );
};

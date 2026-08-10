import { FunctionComponent } from "react";
import { ErrorTypes, ValidationError } from "./state/types";
import { TaskDef } from "types/common";
import {
  AlertTitle,
  Alert as MuiAlert,
  Box,
  Typography,
  Chip,
} from "@mui/material";
import { WarningCircle } from "@phosphor-icons/react";

interface ServerErrorsDisplayerProps {
  serverErrors: ValidationError[];
  onCleanServerError: () => void;
  onClickReference?: (data: string) => void;
  tasks?: TaskDef[];
}

const titleForServerErrorType = (type: ErrorTypes) => {
  switch (type) {
    case ErrorTypes.WORKFLOW:
      return "Workflow was not saved";
    case ErrorTypes.RUN_ERROR:
      return "Could not run workflow";
    default:
      return "Error";
  }
};

const DEFAULT_TASKS: TaskDef[] = [];

export const ServerErrorsDisplayer: FunctionComponent<
  ServerErrorsDisplayerProps
> = ({
  serverErrors,
  tasks = DEFAULT_TASKS,
  onCleanServerError,
  onClickReference,
}) => {
  function extractTaskIndex(input: string): number | null {
    const match = input.match(/tasks\[(\d+)\]/);
    return match ? parseInt(match[1], 10) : null;
  }

  const handleClickValidationError = (path: string) => {
    const targetTaskIndex = extractTaskIndex(path);
    const taskRefName =
      targetTaskIndex != null
        ? tasks[targetTaskIndex]?.taskReferenceName
        : null;
    if (taskRefName && onClickReference) {
      onClickReference(`"taskReferenceName": "${taskRefName}"`);
    }
  };

  return (
    <MuiAlert
      severity="warning"
      onClose={onCleanServerError}
      sx={{
        borderRadius: 1,
        backgroundColor: "transparent",
        "& .MuiAlert-message": {
          width: "100%",
          padding: 0,
        },
        "& .MuiAlert-icon": {
          alignSelf: "flex-start",
          marginTop: "-5px",
        },
      }}
    >
      {serverErrors?.map(({ message, type, validationErrors }) => (
        <Box key={`${message}-${type}`} sx={{ mb: 2 }}>
          <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
            <AlertTitle
              sx={{
                fontSize: "1.1rem",
                fontWeight: 600,
                marginBottom: 0,
                color: "#ff9800",
                flex: 1,
              }}
            >
              {titleForServerErrorType(type)}
            </AlertTitle>
            <Chip
              label={type}
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

          <Box
            sx={{
              p: 2,
              mb: 2,
              borderRadius: 1,
              backgroundColor: "rgba(255, 255, 255, 0.05)",
              border: "1px solid rgba(255, 255, 255, 0.1)",
            }}
          >
            <Typography
              variant="body2"
              sx={{
                color: "#ffffff",
                fontWeight: 500,
                lineHeight: 1.5,
              }}
            >
              {message}
            </Typography>
          </Box>

          {validationErrors && validationErrors.length > 0 && (
            <>
              <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
                <WarningCircle
                  size={16}
                  color="#ff6b6b"
                  style={{ marginRight: 8 }}
                />
                <Typography
                  variant="body2"
                  sx={{ fontWeight: 600, color: "#ff6b6b" }}
                >
                  Validation Errors:
                </Typography>
                <Chip
                  label={`${validationErrors.length} error${validationErrors.length > 1 ? "s" : ""}`}
                  size="small"
                  sx={{
                    ml: 1,
                    backgroundColor: "#f44336",
                    color: "white",
                    fontSize: "0.7rem",
                    height: 20,
                    fontWeight: 500,
                  }}
                />
              </Box>

              <Box sx={{ ml: 2 }}>
                {validationErrors?.map((validationError) => (
                  <Box
                    key={`${validationError?.path}-${validationError?.message?.slice(0, 20)}`}
                    sx={{
                      p: 2,
                      mb: 1,
                      borderRadius: 1,
                      backgroundColor: "rgba(255, 255, 255, 0.03)",
                      border: "1px solid rgba(255, 255, 255, 0.1)",
                      cursor: "pointer",
                      transition: "all 0.2s ease-in-out",
                      "&:hover": {
                        backgroundColor: "rgba(255, 255, 255, 0.08)",
                        borderColor: "rgba(255, 255, 255, 0.2)",
                      },
                    }}
                    onClick={() =>
                      handleClickValidationError(validationError?.path ?? "")
                    }
                  >
                    <Typography
                      variant="body2"
                      sx={{
                        color: "#ffffff",
                        fontWeight: 400,
                        lineHeight: 1.4,
                      }}
                    >
                      {validationError?.message}
                    </Typography>
                    {validationError?.path && (
                      <Typography
                        variant="caption"
                        sx={{
                          color: "#b0b0b0",
                          fontSize: "0.7rem",
                          display: "block",
                          mt: 0.5,
                        }}
                      >
                        Path: {validationError.path}
                      </Typography>
                    )}
                  </Box>
                ))}
              </Box>
            </>
          )}
        </Box>
      ))}
    </MuiAlert>
  );
};

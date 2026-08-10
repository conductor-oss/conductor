import { Box } from "@mui/material";
import { CaretUp, Info, WarningCircle, XCircle } from "@phosphor-icons/react";
import { useMemo } from "react";
import { ActorRef } from "xstate";
import ImportSummaryComponent from "./ImportSummary";
import { ServerErrorsDisplayer } from "./ServerErrorDisplayer";
import { useErrorInspectorActor } from "./state/hook";
import { ErrorInspectorMachineEvents } from "./state/types";
import { TaskErrorsDisplayer } from "./TaskErrorsDisplayer";
import { WorkflowErrorsDisplayer } from "./WorkflowErrorDisplayer";

interface ErrorInspectorProps {
  errorInspectorActor: ActorRef<ErrorInspectorMachineEvents>;
}

const ErrorInspector = ({ errorInspectorActor }: ErrorInspectorProps) => {
  const [
    {
      workflowErrors,
      taskErrors,
      unreachableTaskErrors,
      serverErrors,
      errorCount,
      taskErrorsExpanded,
      workflowErrorsExpanded,
      referenceTaskErrorsExpanded,
      referenceWorkflowErrorsExpanded,
      taskReferenceErrors,
      workflowReferenceErrors,
      warningCount,
      expanded,
      tasks,
      runWorkflowErrors,
    },
    {
      handleToggleTaskErrors,
      handleToggleWorkflowErrors,
      handleCleanServerErrors,
      handleToggleTaskReferenceErrors,
      handleToggleWorkflowReferenceErrors,
      handleClickReference,
      handleToggleErrorInspector,
      handleJumpToFirstError,
    },
  ] = useErrorInspectorActor(errorInspectorActor);
  const [statusIcon, barBackgroundColor, problemCount] = useMemo(() => {
    const problemCount = errorCount + warningCount;
    if (errorCount > 0) {
      return [<XCircle size={24} key="circle" />, "#880000", problemCount];
    }
    if (warningCount > 0) {
      return [
        <WarningCircle size={24} key="warning" />,
        "#c69035",
        problemCount,
      ];
    }
    return [
      <Info size={24} color="#100524" key="info" />,
      "#9FDCAA",
      problemCount,
    ];
  }, [errorCount, warningCount]);

  const handleOnClickReference = (data: string) => {
    handleClickReference!(data);
  };

  const textColor = () => {
    if (problemCount) {
      return "#FFFFFF";
    }
    return "#100524";
  };
  const problemLabel = useMemo(() => {
    if (problemCount === warningCount) {
      return `${problemCount} ${
        warningCount === 1 ? "warning" : "warnings"
      } found.`;
    }
    return `${problemCount} ${
      problemCount === 1 ? "problem" : "problems"
    } found.`;
  }, [problemCount, warningCount]);
  return (
    <Box
      id="error-inspector-container"
      style={{
        position: "absolute",
        display: "flex",
        flexDirection: "column",
        bottom: 0,
        left: 0,
        width: "100%",
        height: expanded ? "350px" : "auto",
        background: "#222222",
        color: "#ffffff",
        zIndex: 12,
      }}
    >
      <Box
        onClick={handleToggleErrorInspector}
        style={{
          display: "flex",
          alignItems: "center",
          height: "50px",
          padding: "0 10px",
          fontSize: "12pt",
          background: barBackgroundColor,
          width: "100%",
          cursor: "pointer",
        }}
      >
        <Box
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            width: "100%",
          }}
        >
          {/* Left side - Status and message */}
          <Box
            id="error-inspector-header"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "12px",
            }}
          >
            <Box
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                width: "32px",
                height: "32px",
                borderRadius: "8px",
                background: "rgba(255, 255, 255, 0.1)",
                backdropFilter: "blur(10px)",
                border: "1px solid rgba(255, 255, 255, 0.2)",
                transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
              }}
            >
              {statusIcon}
            </Box>
            <Box
              style={{
                color: textColor(),
                fontSize: "14px",
                fontWeight: "500",
                letterSpacing: "0.01em",
                lineHeight: "1.4",
              }}
            >
              {problemLabel}
            </Box>
          </Box>

          {/* Right side - Toggle button */}
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: "30px",
              height: "30px",
              borderRadius: "10px",
              background: "rgba(255, 255, 255, 0.08)",
              backdropFilter: "blur(10px)",
              border: "1px solid rgba(255, 255, 255, 0.15)",
              cursor: "pointer",
              transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
              transform: expanded ? "rotate(180deg)" : "rotate(0deg)",
              "&:hover": {
                background: "rgba(255, 255, 255, 0.15)",
                borderColor: "rgba(255, 255, 255, 0.25)",
                transform: expanded
                  ? "rotate(180deg) scale(1.05)"
                  : "rotate(0deg) scale(1.05)",
              },
            }}
          >
            <CaretUp
              size={20}
              color={textColor()}
              style={{
                transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
              }}
            />
          </Box>
        </Box>
      </Box>
      {expanded ? (
        <Box
          style={{
            color: "white",
            position: "relative",
            height: "100%",
            overflowY: "auto",
            overflowX: "hidden",
          }}
        >
          <ImportSummaryComponent errorInspectorActor={errorInspectorActor} />
          {serverErrors.length > 0 ? (
            <ServerErrorsDisplayer
              onCleanServerError={() => handleCleanServerErrors!()}
              serverErrors={serverErrors}
              onClickReference={handleOnClickReference}
              tasks={tasks}
            />
          ) : null}
          {runWorkflowErrors.length > 0 ? (
            <ServerErrorsDisplayer
              onCleanServerError={() => handleCleanServerErrors!()}
              serverErrors={runWorkflowErrors}
              onClickReference={handleOnClickReference}
              tasks={tasks}
            />
          ) : null}
          {taskErrors.length > 0 ? (
            <TaskErrorsDisplayer
              taskErrors={taskErrors}
              onToggleExpand={() => handleToggleTaskErrors!()}
              expanded={taskErrorsExpanded}
            />
          ) : null}
          {workflowErrors.length > 0 ? (
            <WorkflowErrorsDisplayer
              expanded={workflowErrorsExpanded}
              onToggleExpand={() => handleToggleWorkflowErrors!()}
              workflowErrors={workflowErrors}
              onClickReference={() => handleJumpToFirstError()}
            />
          ) : null}
          {unreachableTaskErrors.length > 0 ? (
            <TaskErrorsDisplayer
              taskErrors={unreachableTaskErrors}
              title="Unreachable tasks"
              expanded={referenceTaskErrorsExpanded}
              onToggleExpand={() => handleToggleTaskReferenceErrors!()}
              onClickReference={handleOnClickReference}
            />
          ) : null}
          {taskReferenceErrors.length > 0 ? (
            <TaskErrorsDisplayer
              expanded={referenceTaskErrorsExpanded}
              taskErrors={taskReferenceErrors}
              onToggleExpand={() => handleToggleTaskReferenceErrors!()}
              title="Task missing references"
              onClickReference={handleOnClickReference}
            />
          ) : null}
          {workflowReferenceErrors.length > 0 ? (
            <WorkflowErrorsDisplayer
              expanded={referenceWorkflowErrorsExpanded}
              onToggleExpand={() => handleToggleWorkflowReferenceErrors!()}
              workflowErrors={workflowReferenceErrors}
              title="Workflow missing references"
              onClickReference={handleOnClickReference}
            />
          ) : null}
        </Box>
      ) : null}
    </Box>
  );
};

export default ErrorInspector;

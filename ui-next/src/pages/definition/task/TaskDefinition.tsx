import { Monaco } from "@monaco-editor/react";
import {
  Box,
  CircularProgress,
  LinearProgress,
  Paper,
  Tab,
  Tabs,
  Theme,
} from "@mui/material";
import { useMachine } from "@xstate/react";
import { DocLink } from "components/DocLink";
import { MessageContext } from "components/v1/layout/MessageContext";
import { ConductorSectionHeader } from "components/v1/layout/section/ConductorSectionHeader";
import _get from "lodash/get";
import _isString from "lodash/isString";
import TaskDefinitionDialogs from "pages/definition/task/dialogs/TaskDefinitionDialogs";
import TaskDefinitionFormV1 from "pages/definition/task/form/TaskDefinitionForm";
import { taskDefinitionMachine } from "pages/definition/task/state";
import { useTaskDefinition } from "pages/definition/task/state/hook";
import {
  TaskDefinitionMachineEventType,
  TaskDefinitionMachineState,
} from "pages/definition/task/state/types";
import { useContext, useEffect, useMemo, useRef } from "react";
import { Helmet } from "react-helmet";
import { useLocation, useParams } from "react-router";
import SectionContainer from "shared/SectionContainer";
import { useAuth } from "shared/auth";
import { newTaskTemplate } from "templates/JSONSchemaWorkflow";
import { colors } from "theme/tokens/variables";
import { TaskDefinitionDto } from "types";
import { DOC_LINK_URL } from "utils/constants/docLink";
import { NEW_TASK_DEF_URL, TASK_DEF_URL } from "utils/constants/route";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useAuthHeaders } from "utils/query";
import { randomChars } from "utils/strings";
import { SaveProtectionPrompt } from "./SaveProtectionPrompt";
import TaskDefinitionButtons from "./TaskDefinitionButtons";
import TaskDefinitionDiffEditor from "./TaskDefinitionDiffEditor";
import { TASK_DEFINITION_SAVED_SUCCESSFULLY_MESSAGE } from "./state/helpers";

// TODO: Should refactor this when we apply dark mode
// The dark mode styles should be configured in theme
const getBackgroundColorOfForm = ({
  theme,
  isInFormView,
}: {
  theme: Theme;
  isInFormView: boolean;
}) => {
  if (isInFormView) {
    return colors.white;
  }

  if (theme.palette?.mode === "dark") {
    return colors.gray00;
  }

  return colors.gray14;
};

/**
 * NOTE:
 * 1. Single mode: After POST successfully will redirect to task detail page
 * 2. Bulk mode or Save and Create New: Stay at the same page with current state
 * 3. Test task: execute a workflow with current task
 * 4. Form mode doesn't have bulk creation
 */
export default function TaskDefinition() {
  const pushHistory = usePushHistory();
  const { setMessage } = useContext(MessageContext);

  const { conductorUser } = useAuth();
  const authHeaders = useAuthHeaders();
  const editorRefs = useRef<Monaco>(null);
  const location = useLocation();
  const params = useParams();
  // Memoize isNewTaskDef to prevent unnecessary re-renders when location changes but pathname stays the same
  const isNewTaskDef = useMemo(
    () => location.pathname === NEW_TASK_DEF_URL,
    [location.pathname],
  );

  // Stabilize params to prevent unnecessary re-renders
  // Only re-compute when the actual param values change, not when the params object reference changes
  const paramName = _get(params, "name");
  const stableParams = useMemo(() => {
    return { name: paramName };
  }, [paramName]);

  // Defines a Template and puts the name of the url.
  const initTaskDefinition = useMemo(
    () => ({
      ...newTaskTemplate(conductorUser?.id || "example@email.com"),
      name: isNewTaskDef ? `task-${randomChars(6)}` : stableParams.name,
    }),
    [stableParams.name, isNewTaskDef, conductorUser?.id],
  ) as Partial<TaskDefinitionDto>;

  const taskJsonString = JSON.stringify(initTaskDefinition, null, 2);
  // Create Task state machine
  const [current, , taskDefActor] = useMachine(taskDefinitionMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      originTaskDefinitionString: taskJsonString, // Not necessary
      modifiedTaskDefinitionString: taskJsonString, // Not necessary
      originTaskDefinition: initTaskDefinition,
      modifiedTaskDefinition: initTaskDefinition,
      originTaskDefinitions: [initTaskDefinition],
      isNewTaskDef,
      user: conductorUser,
      authHeaders,
      couldNotParseJson: false,
    },
    actions: {
      redirectToNewTask: () => {
        pushHistory(NEW_TASK_DEF_URL);
      },
      redirectToEditTask: ({ modifiedTaskDefinition }) => {
        pushHistory(
          `${TASK_DEF_URL.BASE}/${encodeURIComponent(
            modifiedTaskDefinition.name as string,
          )}`,
        );
      },
      redirectToTaskList: () => {
        pushHistory(TASK_DEF_URL.BASE);
      },
      setErrorMessage: (__, data: any) => {
        setMessage({
          text: data?.data?.message,
          severity: "error",
        });
      },
      showSaveSuccessMessage: () => {
        setMessage({
          text: TASK_DEFINITION_SAVED_SUCCESSFULLY_MESSAGE,
          severity: "success",
        });
      },
    },
  });

  const [
    {
      formActor,
      isFetching,
      modifiedTaskDefinition,
      couldNotParseJson,
      isReady,
    },
    { toggleFormMode },
  ] = useTaskDefinition(taskDefActor);

  const isInFormView =
    current.matches([
      TaskDefinitionMachineState.READY,
      TaskDefinitionMachineState.MAIN_CONTAINER,
      TaskDefinitionMachineState.FORM,
    ]) && formActor;

  useEffect(() => {
    const name = stableParams.name;
    if (!isNewTaskDef && name != null) {
      taskDefActor.send({
        type: TaskDefinitionMachineEventType.SET_TASK_DEFINITION,
        name,
        isNew: isNewTaskDef,
      });
    }
  }, [stableParams.name, isNewTaskDef, taskDefActor]);

  const sectionTitle = useMemo<string>(() => {
    if (isNewTaskDef) return "New Task";

    if (_isString(modifiedTaskDefinition?.name)) {
      return modifiedTaskDefinition?.name;
    }

    return "";
  }, [isNewTaskDef, modifiedTaskDefinition]);

  return (
    <Box>
      <Helmet>
        <title>
          Task Definition -&nbsp;
          {isNewTaskDef
            ? "NEW"
            : _isString(modifiedTaskDefinition?.name)
              ? modifiedTaskDefinition?.name
              : ""}
        </title>
      </Helmet>
      <TaskDefinitionDialogs taskDefActor={taskDefActor} />
      <SaveProtectionPrompt taskDefActor={taskDefActor} />
      <SectionContainer
        header={
          <ConductorSectionHeader
            id="task-definition-header-section"
            title={sectionTitle}
            breadcrumbItems={[
              { label: "Task Definitions", to: TASK_DEF_URL.BASE },
              { label: sectionTitle, to: TASK_DEF_URL.BASE },
            ]}
            buttonsComponent={
              <TaskDefinitionButtons taskDefActor={taskDefActor} />
            }
          />
        }
      >
        {isFetching && <LinearProgress />}
        <Paper
          id="task-definition-container"
          variant="outlined"
          sx={{ height: "fit-content", borderRadius: 0 }}
        >
          <Box sx={{ height: "100%", padding: 6 }}>
            {isReady ? (
              <>
                <Box sx={{ position: "relative" }}>
                  <Tabs
                    value={isInFormView ? 1 : 0}
                    style={{
                      marginBottom: 0,
                      borderBottom: "1px solid rgba(0,0,0,0.2)",
                    }}
                    onChange={(__: any, newValue: number) =>
                      toggleFormMode(!!newValue)
                    }
                  >
                    <Tab label="Task" value={1} disabled={couldNotParseJson} />
                    <Tab label="Code" value={0} />
                  </Tabs>

                  <DocLink
                    label="Task docs"
                    url={DOC_LINK_URL.TASK_DEFINITION}
                  />
                </Box>
                <Box
                  id="task-def-limit-height-wrapper"
                  sx={{
                    height: "calc(100vh - 180px)",
                    overflow: "scroll",
                    color: (theme) =>
                      theme.palette?.mode === "dark"
                        ? colors.gray14
                        : undefined,
                    backgroundColor: (theme) =>
                      getBackgroundColorOfForm({
                        theme,
                        isInFormView,
                      }),
                  }}
                >
                  {isInFormView ? (
                    <TaskDefinitionFormV1 formActor={formActor} />
                  ) : null}
                  {current.matches([
                    TaskDefinitionMachineState.READY,
                    TaskDefinitionMachineState.MAIN_CONTAINER,
                    TaskDefinitionMachineState.DIFF_EDITOR,
                  ]) ||
                  current.matches([
                    TaskDefinitionMachineState.READY,
                    TaskDefinitionMachineState.MAIN_CONTAINER,
                    TaskDefinitionMachineState.EDITOR,
                  ]) ? (
                    <TaskDefinitionDiffEditor
                      taskDefActor={taskDefActor}
                      ref={editorRefs}
                    />
                  ) : null}
                </Box>
              </>
            ) : (
              <Box
                sx={{
                  height: "calc(100vh - 180px)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <CircularProgress size={20} />
              </Box>
            )}
          </Box>
        </Paper>
      </SectionContainer>
    </Box>
  );
}

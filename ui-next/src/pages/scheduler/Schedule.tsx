import { Monaco } from "@monaco-editor/react";
import {
  Box,
  CircularProgress,
  Grid,
  Paper,
  SxProps,
  Tab,
  Tabs,
  Theme,
  useMediaQuery,
} from "@mui/material";
import { LinearProgress } from "components";
import { DocLink } from "components/ui/DocLink";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { MessageContext } from "components/providers/messageContext";
import { ConductorSectionHeader } from "components/layout/section/ConductorSectionHeader";
import { IdempotencyStrategyEnum } from "pages/runWorkflow/types";
import { useCallback, useContext, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useLocation, useParams } from "react-router";
import SectionContainer from "components/ui/layout/SectionContainer";
import { colors } from "theme/tokens/variables";
import { IObject } from "types/common";
import { DOC_LINK_URL } from "utils/constants/docLink";
import { SCHEDULER_DEFINITION_URL } from "utils/constants/route";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { getErrors } from "utils/index";
import { useWorkflowDefsByVersions } from "utils/query";
import { CronExpressionSection } from "./components/CronExpressionSection";
import { ScheduleTimingSection } from "./components/ScheduleTimingSection";
import { WorkflowConfigSection } from "./components/WorkflowConfigSection";
import { useCronExpression } from "./hooks/useCronExpression";
import { useScheduleFormHandlers } from "./hooks/useScheduleFormHandlers";
import { useScheduleState } from "./hooks/useScheduleState";
import { useWorkflowConfig } from "./hooks/useWorkflowConfig";
import { SaveProtectionPrompt } from "./SaveProtectionPrompt";
import ScheduleButtons from "./ScheduleButtons";
import ScheduleDiffEditor from "./ScheduleDiffEditor";
import { useSaveSchedule, useSchedule } from "./schedulerHooks";
import {
  codeToFormData,
  formToCodeData,
  getDateFromField,
  JSONParse,
} from "./utils/scheduleTransformers";

export type ScheduleType = {
  name: string;
  description?: string;
  cronExpression: string;
  paused: boolean;
  runCatchupScheduleInstances: boolean;
  workflowType: string | null;
  workflowVersion: string | null;
  workflowVersions: string[];
  workflowInputTemplate: string;
  taskToDomain: string;
  workflowCorrelationId: string;
  workflowIdempotencyKey?: string;
  workflowIdempotencyStrategy?: IdempotencyStrategyEnum;
  workflowDef: string | null;
  externalInputPayloadStoragePath?: string;
  scheduleStartTime: string | number;
  scheduleEndTime: string | number;
  priority: string;
  zoneId?: string;
  startWorkflowRequest?: Record<string, unknown>;
};

export function Schedule() {
  const { setMessage } = useContext(MessageContext);
  const location = useLocation();
  const latestExecution = useMemo(() => location.state?.execution, [location]);
  const [selectedTemplate, setSelectedTemplate] = useState("");
  const [timeoutHandler, setTimeoutHandler] = useState<ReturnType<
    typeof setTimeout
  > | null>(null);

  const params = useParams();
  const navigate = usePushHistory();
  const isNewScheduleDef = location.pathname === SCHEDULER_DEFINITION_URL.NEW;
  let scheduleNameFromUrl = "New Scheduler";
  const isMDWidth = useMediaQuery((theme: Theme) => theme.breakpoints.up("md"));

  if (!isNewScheduleDef) {
    scheduleNameFromUrl = params.name || "New Scheduler";
  }

  const { data: schedule, isLoading } = useSchedule(
    isNewScheduleDef ? null : scheduleNameFromUrl,
  );

  const workflowDefByVersions = useWorkflowDefsByVersions();

  // Custom hooks for state management
  const {
    scheduleState,
    setScheduleState,
    original,
    initializeFromSchedule,
    initializeFromExecution,
  } = useScheduleState(latestExecution, schedule);

  const {
    workflowNames,
    workflowVersions,
    setWorkflowType,
    setWorkflowVersion,
  } = useWorkflowConfig(
    workflowDefByVersions,
    scheduleState.workflowType || null,
    scheduleState.workflowVersions,
    scheduleState.workflowInputTemplate,
  );

  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errors, setErrors] = useState<IObject | null>(null);
  const [couldNotParseJson, setCouldNotParseJson] = useState<boolean>(false);

  const clearError = useCallback(
    (field: string) => {
      if (errors) {
        const updatedErrors = { ...errors };
        delete updatedErrors[field];
        setErrors(updatedErrors);
      }
    },
    [errors, setErrors],
  );

  const formHandlers = useScheduleFormHandlers(
    scheduleState,
    setScheduleState,
    setErrors,
    clearError,
    errors,
    setCouldNotParseJson,
    () => {}, // setHighlightedPart will be handled by cron hook
  );

  const cronHook = useCronExpression(
    scheduleState.cronExpression,
    scheduleState.zoneId || "UTC",
    (error) => {
      if (error) {
        setErrors((prevErrors: IObject | null) => ({
          ...prevErrors,
          cronExpression: error,
        }));
      } else {
        clearError("cronExpression");
      }
    },
  );

  const { mutate: saveSchedule, isLoading: isSavingSchedule } = useSaveSchedule(
    {
      onSuccess: () => {
        setMessage({
          text: "Schedule definition saved successfully.",
          severity: "success",
        });
        navigate(SCHEDULER_DEFINITION_URL.BASE);
      },

      onError: async (response: Response) => {
        const errors = await getErrors(response, (res) => ({
          message: `Error - ${res.status} - ${res.statusText}`,
        }));
        console.error("Errors: ", errors);
        setErrors(errors);
        if (response.status === 403) {
          setErrorMessage(
            `Error - You don't have permissions to schedule the selected workflow.`,
          );
        } else {
          if (errors.message) {
            setErrorMessage(`Error - ${response.status} - ${errors.message}`);
          } else {
            setErrorMessage(
              `Error - ${response.status} - ${response.statusText}`,
            );
          }
        }
        setTimeoutHandler(setTimeout(() => setErrorMessage(null), 5000));
        cancelConfirmSave();
      },
    },
  );

  // Memoized handlers using custom hooks
  const handleWorkflowTypeChange = useCallback(
    (workflowType: string) => {
      setCouldNotParseJson(false);
      if (errors && errors["startWorkflowRequest.name"]) {
        clearError("startWorkflowRequest.name");
      }
      const result = setWorkflowType(workflowType);
      setScheduleState((prevState) => ({
        ...prevState,
        workflowVersions: result.workflowVersions,
        workflowVersion: "",
        workflowType: workflowType,
        workflowCorrelationId: "",
        workflowInputTemplate: result.workflowInputTemplate,
      }));
    },
    [setWorkflowType, setScheduleState, errors, clearError],
  );

  const handleWorkflowVersionChange = useCallback(
    (workflowVersion: string | null) => {
      const result = setWorkflowVersion(
        workflowVersion,
        scheduleState.workflowType || null,
      );
      setScheduleState((prevState) => ({
        ...prevState,
        workflowVersion:
          workflowVersion === "Latest version" ? "" : workflowVersion,
        workflowInputTemplate: result.workflowInputTemplate,
      }));
    },
    [setWorkflowVersion, setScheduleState, scheduleState.workflowType],
  );

  // Initialize state when schedule data changes
  useMemo(() => {
    if (schedule) {
      initializeFromSchedule(schedule);
    }
  }, [schedule, initializeFromSchedule]);

  useMemo(() => {
    if (latestExecution?.workflowName) {
      initializeFromExecution(latestExecution);
    }
  }, [latestExecution, initializeFromExecution]);

  // Memoized cron expression handler
  const handleCronExpressionChange = useCallback(
    (value: string, timezone: string) => {
      cronHook.setCronExpression(value, timezone);
      setScheduleState((prevState) => ({
        ...prevState,
        cronExpression: value,
      }));
    },
    [cronHook, setScheduleState],
  );

  // Memoized values
  const minWidthCronExpression = useMemo(() => {
    if (selectedTemplate && isMDWidth) {
      return "470px";
    } else if (!selectedTemplate && isMDWidth) {
      return "initial";
    }
    return "100%";
  }, [selectedTemplate, isMDWidth]);

  // Memoized handlers
  const handleZoneIdChange = useCallback(
    (value: string) => {
      formHandlers.setZoneId(value);
      handleCronExpressionChange(scheduleState.cronExpression, value);
    },
    [formHandlers, handleCronExpressionChange, scheduleState.cronExpression],
  );

  const clearErrors = useCallback(() => {
    if (timeoutHandler) {
      clearTimeout(timeoutHandler);
    }
    setErrorMessage("");
    setErrors(null);
  }, [timeoutHandler]);

  const saveScheduleSubmit = useCallback(() => {
    clearErrors();

    const start = getDateFromField(scheduleState.scheduleStartTime);
    const to = getDateFromField(scheduleState.scheduleEndTime);

    let input;
    try {
      input = JSONParse(scheduleState.workflowInputTemplate);
    } catch {
      setErrorMessage("Invalid JSON: input params");
      return;
    }

    let taskToDomain;
    try {
      taskToDomain = JSONParse(scheduleState.taskToDomain);
    } catch {
      setErrorMessage("Invalid JSON: tasks to domain mapping");
      return;
    }

    const body = JSON.stringify({
      id: schedule?.id,
      paused: scheduleState.paused,
      runCatchupScheduleInstances: scheduleState.runCatchupScheduleInstances,
      name: scheduleState.name,
      description: scheduleState.description,
      cronExpression: scheduleState.cronExpression,
      scheduleStartTime: start,
      scheduleEndTime: to,
      startWorkflowRequest: {
        name: scheduleState.workflowType,
        version: scheduleState.workflowVersion,
        input,
        correlationId: scheduleState.workflowCorrelationId,
        idempotencyKey: scheduleState?.workflowIdempotencyKey,
        idempotencyStrategy: scheduleState?.workflowIdempotencyStrategy,
        taskToDomain,
        workflowDef: scheduleState.workflowDef,
        externalInputPayloadStoragePath:
          scheduleState.externalInputPayloadStoragePath,
        priority: scheduleState.priority,
      },
      zoneId: scheduleState.zoneId,
    });

    saveSchedule({ body } as any);
  }, [scheduleState, schedule, clearErrors, setErrorMessage, saveSchedule]);

  const clearScheduleForm = useCallback(() => {
    if (schedule) {
      initializeFromSchedule(schedule);
    } else {
      // Reset to initial state
      setScheduleState({
        name: "",
        description: "",
        cronExpression: "",
        paused: false,
        runCatchupScheduleInstances: false,
        workflowType: null,
        workflowVersion: null,
        workflowVersions: [],
        workflowInputTemplate: "",
        taskToDomain: "",
        workflowCorrelationId: "",
        workflowIdempotencyKey: undefined,
        workflowIdempotencyStrategy: undefined,
        workflowDef: null,
        externalInputPayloadStoragePath: undefined,
        scheduleStartTime: "",
        scheduleEndTime: "",
        priority: "",
        zoneId: "UTC",
      });
    }
    setIsInFormView(1);
  }, [schedule, initializeFromSchedule, setScheduleState]);

  const [isInFormView, setIsInFormView] = useState(1);
  const [isConfirmingSave, setIsConfirmingSave] = useState<boolean>(false);
  const [newData, setNewData] = useState<string>("");
  const [transitionData, setTransitionData] =
    useState<Partial<ScheduleType> | null>(null);
  const [interimString, setInterimString] = useState<string>("");

  const MAX_WIDTH = "920px";
  const containerStyle: SxProps<Theme> = {
    maxWidth: MAX_WIDTH,
    color: (theme) =>
      theme.palette?.mode === "dark" ? colors.gray14 : undefined,
    backgroundColor: (theme) => theme.palette.customBackground.form,
    px: 4,
  };

  const setSaveConfirmationOpen = useCallback(() => {
    setIsConfirmingSave(true);
    setIsInFormView(0);
    if (interimString !== "") {
      const body = codeToFormData(interimString, scheduleState);
      setScheduleState(body);
      setInterimString("");
      setTransitionData(null);
      setNewData(interimString);
    } else {
      const start = getDateFromField(scheduleState.scheduleStartTime);
      const to = getDateFromField(scheduleState.scheduleEndTime);

      let input;
      try {
        input = JSONParse(scheduleState.workflowInputTemplate);
      } catch {
        setErrorMessage("Invalid JSON: Input Params");
        return;
      }

      let taskToDomain;
      try {
        taskToDomain = JSONParse(scheduleState.taskToDomain);
      } catch {
        setErrorMessage("Invalid JSON: Tasks to Domain Mapping");
        return;
      }

      const body = JSON.stringify(
        {
          id: schedule?.id,
          paused: scheduleState.paused,
          runCatchupScheduleInstances:
            scheduleState.runCatchupScheduleInstances,
          name: scheduleState.name,
          description: scheduleState.description,
          cronExpression: scheduleState.cronExpression,
          scheduleStartTime: start,
          scheduleEndTime: to,
          startWorkflowRequest: {
            name: scheduleState.workflowType,
            version: scheduleState.workflowVersion,
            input: input ? input : {},
            correlationId: scheduleState.workflowCorrelationId,
            idempotencyKey: scheduleState?.workflowIdempotencyKey,
            idempotencyStrategy: scheduleState?.workflowIdempotencyStrategy,
            taskToDomain: taskToDomain ? taskToDomain : {},
            externalInputPayloadStoragePath:
              scheduleState.externalInputPayloadStoragePath,
            priority: scheduleState.priority,
          },
          zoneId: scheduleState.zoneId,
        },
        null,
        2,
      );
      setNewData(body);
    }
  }, [
    interimString,
    scheduleState,
    schedule,
    setErrorMessage,
    setScheduleState,
  ]);

  const cancelConfirmSave = useCallback(() => {
    const body = JSON.parse(newData);
    setTransitionData(body);
    setIsConfirmingSave(false);
  }, [newData]);

  const handleChangeTab = useCallback(
    (value: number) => {
      if (value === 0) {
        const body = formToCodeData(scheduleState, schedule);
        setTransitionData(body);
      } else {
        if (interimString !== "") {
          const body = codeToFormData(interimString, scheduleState);
          body.workflowVersions = scheduleState.workflowVersions;
          setScheduleState(body);
          setInterimString("");
          setTransitionData(null);
        } else {
          if (newData) {
            const body = codeToFormData(newData, scheduleState);
            body.workflowVersions = scheduleState.workflowVersions;
            setScheduleState(body);
            setInterimString("");
            setTransitionData(null);
            setNewData("");
          } else {
            const body = codeToFormData(
              JSON.stringify(transitionData),
              scheduleState,
            );
            setScheduleState(body);
          }
        }
      }
      setIsInFormView(value);
    },
    [
      scheduleState,
      schedule,
      interimString,
      newData,
      transitionData,
      setScheduleState,
    ],
  );

  const handleChangeTransitionData = useCallback(
    (data: string) => {
      let parsedData: ScheduleType;
      try {
        parsedData = JSON.parse(data);
        setCouldNotParseJson(false);
      } catch {
        setCouldNotParseJson(true);
        return;
      }
      setScheduleState((prevState) => ({
        ...prevState,
        name: parsedData?.name,
      }));
      setInterimString(data);
    },
    [setScheduleState],
  );

  const diffEditorDidMount = useCallback(
    (editor: Monaco) => {
      const modifiedEditor = editor.getModifiedEditor();
      modifiedEditor.onDidChangeModelContent(() => {
        const maybeText = modifiedEditor.getValue();
        if (typeof maybeText === "string") {
          try {
            JSON.parse(maybeText);
          } catch {
            return;
          }
          const body = codeToFormData(maybeText, scheduleState);
          setNewData(maybeText);
          setScheduleState(body);
        }
      });
    },
    [scheduleState, setScheduleState],
  );

  const initialFormData = useMemo(
    () =>
      isNewScheduleDef
        ? {
            ...codeToFormData(JSON.stringify(original), scheduleState),
            taskToDomain: "",
            workflowInputTemplate: "",
            workflowDef: null,
          }
        : codeToFormData(JSON.stringify(original), scheduleState),
    [isNewScheduleDef, original, scheduleState],
  );

  const changedCodeData = useMemo(
    () => (interimString ? codeToFormData(interimString, scheduleState) : {}),
    [interimString, scheduleState],
  );

  return (
    <Box>
      <Helmet>
        <title>Schedule Editor - {scheduleNameFromUrl}</title>
      </Helmet>
      <SectionContainer
        header={
          <ConductorSectionHeader
            title={scheduleNameFromUrl ? scheduleNameFromUrl : "New Scheduler"}
            breadcrumbItems={[
              { label: "Schedulers", to: SCHEDULER_DEFINITION_URL.BASE },
              {
                label: scheduleNameFromUrl
                  ? scheduleNameFromUrl
                  : "New Scheduler",
                to: "",
              },
            ]}
            buttonsComponent={
              <ScheduleButtons
                isConfirmingSave={isConfirmingSave}
                couldNotParseJson={couldNotParseJson}
                cancelConfirmSave={cancelConfirmSave}
                saveScheduleSubmit={saveScheduleSubmit}
                clearScheduleForm={clearScheduleForm}
                setSaveConfirmationOpen={setSaveConfirmationOpen}
              />
            }
          />
        }
      >
        <SaveProtectionPrompt
          initialFormData={initialFormData}
          data={scheduleState}
          changedCodeData={changedCodeData}
          isInFormView={isInFormView}
          onSave={setSaveConfirmationOpen}
          isSaveInProgress={isSavingSchedule}
          hasErrors={couldNotParseJson}
        />
        {(isLoading || isSavingSchedule) && (
          <LinearProgress sx={{ position: "sticky", top: 0 }} />
        )}
        <Paper
          variant="outlined"
          sx={{ padding: [3, 6], height: "fit-content" }}
        >
          {errorMessage && (
            <SnackbarMessage
              message={errorMessage}
              severity="error"
              onDismiss={() => setErrorMessage(null)}
            />
          )}
          <Grid container sx={{ width: "100%", maxWidth: "100%" }}>
            <Grid size={12}>
              <Box sx={{ position: "relative" }}>
                <Tabs
                  value={isInFormView ? 1 : 0}
                  style={{
                    marginBottom: 0,
                    borderBottom: "1px solid rgba(0,0,0,0.2)",
                  }}
                  onChange={(event, newValue) => handleChangeTab(newValue)}
                >
                  <Tab label="Schedule" value={1} disabled={isConfirmingSave} />
                  <Tab label="Code" value={0} />
                </Tabs>
                <DocLink label="Scheduler docs" url={DOC_LINK_URL.SCHEDULER} />
              </Box>
            </Grid>

            <Grid
              sx={{
                height: "fit-content",
              }}
              size={12}
            >
              {!isLoading ? (
                <Box
                  sx={{
                    overflow: "scroll",
                    color: (theme) =>
                      theme.palette?.mode === "dark"
                        ? colors.gray14
                        : undefined,
                    backgroundColor: (theme) =>
                      theme.palette.customBackground.form,
                  }}
                >
                  {isInFormView ? (
                    <Box sx={{ ...containerStyle }}>
                      <Grid container spacing={4} pt={3} sx={{ width: "100%" }}>
                        <Grid size={12}>
                          <ConductorInput
                            fullWidth
                            required
                            label="Name"
                            id="schedule-name-field"
                            value={scheduleState.name}
                            onTextInputChange={(val) =>
                              formHandlers.setScheduleNewState("name", val)
                            }
                            error={errors?.name !== undefined}
                            helperText={errors ? errors?.name : undefined}
                            tooltip={{
                              title: "Name",
                              content: "Changing name saves as a new schedule.",
                            }}
                          />
                        </Grid>
                        <Grid size={12}>
                          <ConductorInput
                            id="schedule-description-field"
                            label="Description"
                            name="description"
                            multiline
                            minRows={3}
                            fullWidth
                            value={scheduleState.description}
                            onTextInputChange={(value) =>
                              formHandlers.setScheduleNewState(
                                "description",
                                value,
                              )
                            }
                            placeholder="Enter description"
                          />
                        </Grid>
                        <CronExpressionSection
                          cronExpression={cronHook.cronExpression}
                          setCronExpression={handleCronExpressionChange}
                          futureMatches={cronHook.futureMatches}
                          humanizedExpression={cronHook.humanizedExpression}
                          highlightedPart={cronHook.highlightedPart}
                          getHighlightedPart={formHandlers.getHighlightedPart}
                          setHighlightedPart={cronHook.setHighlightedPart}
                          selectedTemplate={selectedTemplate}
                          setSelectedTemplate={setSelectedTemplate}
                          timezone={scheduleState.zoneId || "UTC"}
                          setZoneId={handleZoneIdChange}
                          cronError={errors?.cronExpression}
                          minWidthCronExpression={minWidthCronExpression}
                        />
                        <WorkflowConfigSection
                          workflowType={scheduleState.workflowType || null}
                          setWorkflowType={handleWorkflowTypeChange}
                          workflowVersion={
                            scheduleState.workflowVersion !== undefined
                              ? scheduleState.workflowVersion
                              : null
                          }
                          setWorkflowVersion={handleWorkflowVersionChange}
                          workflowVersions={workflowVersions}
                          workflowNames={workflowNames}
                          workflowInputTemplate={
                            scheduleState.workflowInputTemplate || ""
                          }
                          setWorkflowInputTemplate={
                            formHandlers.setWorkflowInputTemplatesState
                          }
                          workflowCorrelationId={
                            scheduleState.workflowCorrelationId || ""
                          }
                          setWorkflowCorrelationId={
                            formHandlers.setWorkflowCorrelationIdState
                          }
                          idempotencyValues={{
                            idempotencyKey:
                              scheduleState?.workflowIdempotencyKey,
                            idempotencyStrategy:
                              scheduleState?.workflowIdempotencyStrategy,
                          }}
                          handleIdempotencyValues={
                            formHandlers.handleIdempotencyValues
                          }
                          errors={errors}
                        />
                        <ScheduleTimingSection
                          scheduleStartTime={scheduleState.scheduleStartTime}
                          scheduleEndTime={scheduleState.scheduleEndTime}
                          handleScheduleStartTime={
                            formHandlers.handleScheduleStartTime
                          }
                          handleScheduleEndTime={
                            formHandlers.handleScheduleEndTime
                          }
                          taskToDomain={scheduleState.taskToDomain}
                          setWorkflowTasksToDomainState={
                            formHandlers.setWorkflowTasksToDomainState
                          }
                          paused={scheduleState.paused}
                          setCronPausedState={formHandlers.setCronPausedState}
                        />
                      </Grid>
                    </Box>
                  ) : (
                    <Box
                      style={{
                        height: "calc(100vh - 370px)",
                        overflow: "scroll",
                      }}
                    >
                      <ScheduleDiffEditor
                        original={original}
                        data={transitionData}
                        newData={newData}
                        handleChange={handleChangeTransitionData}
                        isConfirmingSave={isConfirmingSave}
                        handleDiffEditorMount={diffEditorDidMount}
                      />
                    </Box>
                  )}
                </Box>
              ) : (
                <Box
                  sx={{
                    height: "calc(100vh - 420px)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <CircularProgress size={20} />
                </Box>
              )}
            </Grid>
          </Grid>
        </Paper>
      </SectionContainer>
    </Box>
  );
}

import { Box, Grid } from "@mui/material";
import { Button } from "components";
import Paper from "components/ui/Paper";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import ConductorInput from "components/ui/inputs/ConductorInput";
import ResetIcon from "components/icons/ResetIcon";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import IdempotencyForm from "pages/runWorkflow/IdempotencyForm";
import { editor, type EditorOptions } from "shared/editor";
import { SMALL_EDITOR_DEFAULT_OPTIONS } from "utils/constants";
import { useLocalStorage } from "utils/localstorage";
import { ActorRef } from "xstate";
import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "../state";
import { RunWorkflowHistoryTable } from "./RunWorkflowHistoryTable";
import {
  RunMachineEvents,
  RunWorkflowParamType,
  useRunTabActor,
} from "./state";

interface RunWorkFlowFormProps {
  runTabActor: ActorRef<RunMachineEvents>;
  workflowDefinitionActor: ActorRef<WorkflowDefinitionEvents>;
}

const additionalEditorOptions: EditorOptions = {
  scrollBeyondLastLine: false,
  wrappingStrategy: "advanced",
  lightbulb: { enabled: editor.ShowLightbulbIconMode.On },
  quickSuggestions: true,
  lineNumbers: "on",
  wordWrap: "on",
  glyphMargin: false,
  folding: false,
  lineDecorationsWidth: 10,
  lineNumbersMinChars: 0,
  renderLineHighlight: "none",
  hideCursorInOverviewRuler: false,
  overviewRulerBorder: false,
  automaticLayout: true, // Important
};

export const RunWorkFlowForm = ({
  runTabActor,
  workflowDefinitionActor,
}: RunWorkFlowFormProps) => {
  const [
    {
      currentWf,
      input,
      correlationId,
      taskToDomain,
      isRunning,
      popoverMessage,
      idempotencyKey,
      idempotencyStrategy,
      focusInput,
    },
    {
      handleChangeInputParams,
      handleChangeCorrelationId,
      handleChangeTasksToDomain,
      handleClearForm,

      handlePopoverMessage,
      handleFillAllFields,
      handleChangeIdempotencyValues,
    },
  ] = useRunTabActor(runTabActor);

  const [workflowHistory, setWorkflowHistory] = useLocalStorage(
    "workflowHistory",
    [],
  );
  const handlefillReRunWfFields = (data: RunWorkflowParamType) => {
    const payload = {
      correlationId: data.correlationId,
      input: JSON.stringify(data.input, null, 2) ?? "",
      taskToDomain: JSON.stringify(data.taskToDomain, null, 2) ?? "",
      idempotencyKey: data.idempotencyKey,
      idempotencyStrategy: data.idempotencyStrategy,
    };
    handleFillAllFields(payload);
  };

  // Routed through the definition machine so this behaves exactly like the
  // toolbar Execute: unsaved edits are saved first, and running from this tab
  // keeps the run actor alive so it uses the values on screen.
  const handleExecute = () => {
    workflowDefinitionActor.send({
      type: DefinitionMachineEventTypes.HANDLE_SAVE_AND_RUN,
    });
  };

  return (
    <Box sx={{ minHeight: "100%", p: 6 }}>
      <Paper
        variant="outlined"
        sx={{
          padding: 6,
        }}
      >
        {popoverMessage && (
          <SnackbarMessage
            severity={popoverMessage?.severity || ""}
            message={popoverMessage?.text || ""}
            onDismiss={() => handlePopoverMessage(null)}
          />
        )}
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorCodeBlockInput
              label="Input params"
              value={input}
              onChange={handleChangeInputParams}
              autoFocus={focusInput}
              options={{
                ...SMALL_EDITOR_DEFAULT_OPTIONS,
                ...additionalEditorOptions,
              }}
            />
          </Grid>
          <IdempotencyForm
            idempotencyValues={{
              idempotencyKey: idempotencyKey,
              idempotencyStrategy: idempotencyStrategy,
            }}
            onChange={handleChangeIdempotencyValues}
          />
          <Grid size={12}>
            <ConductorInput
              id="correlation-id-field"
              label="Correlation id"
              fullWidth
              value={correlationId}
              onTextInputChange={handleChangeCorrelationId}
            />
          </Grid>
          <Grid size={12}>
            <ConductorCodeBlockInput
              label="Tasks to domain mapping"
              value={taskToDomain}
              onChange={handleChangeTasksToDomain}
              options={{
                ...SMALL_EDITOR_DEFAULT_OPTIONS,
                ...additionalEditorOptions,
              }}
            />
          </Grid>
          <Grid display="flex" justifyContent="flex-end" gap={2} size={12}>
            <Button
              id="clear-info-btn"
              variant="text"
              onClick={handleClearForm}
              startIcon={<ResetIcon />}
            >
              Reset
            </Button>
            <Button
              id="run-tab-execute-btn"
              data-testid="run-tab-execute-button"
              variant="contained"
              onClick={handleExecute}
              disabled={isRunning}
              startIcon={<RocketLaunchIcon />}
            >
              Execute
            </Button>
          </Grid>
          <Grid mt={5} size={12}>
            <RunWorkflowHistoryTable
              workflowName={currentWf?.name}
              fillReRunWfFields={handlefillReRunWfFields}
              workflowHistory={workflowHistory}
              setWorkflowHistory={setWorkflowHistory}
            />
          </Grid>
        </Grid>
      </Paper>
    </Box>
  );
};

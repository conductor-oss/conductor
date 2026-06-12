import { Box, CircularProgress, Grid } from "@mui/material";
import { useInterpret } from "@xstate/react";
import Button from "components/ui/buttons/MuiButton";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import _path from "lodash/fp/path";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import _last from "lodash/last";
import { getWorkflowDefinitionByNameAndVersion } from "pages/definition/commonService";
import TaskFormSection from "pages/definition/EditorPanel/TaskFormTab/forms/TaskFormSection";
import { TaskFormProps } from "pages/definition/EditorPanel/TaskFormTab/forms/types";
import IdempotencyForm from "pages/runWorkflow/IdempotencyForm";
import { IdempotencyStrategyEnum } from "pages/runWorkflow/types";
import { useMemo } from "react";
import { TaskType } from "types/common";
import { WORKFLOW_DEFINITION_URL } from "utils/constants/route";
import { updateField } from "utils/fieldHelpers";
import { openInNewTab } from "utils/helpers";
import { useAuthHeaders } from "utils/query";
import {
  handleChangeIdempotencyValues,
  updateInputParametersCommon,
} from "../../helpers";
import { MaybeVariable } from "../MaybeVariable";
import { Optional } from "../OptionalFieldForm";
import {
  StartSubWfNameVersionMachineContext,
  startSubWfNameVersionMachine,
} from "./state";
import { useStartSubWfNameVersionMachine } from "./state/hook";

const START_WORKFLOW_INPUT_PATH = "inputParameters.startWorkflow.input";
const START_WORKFLOW_CORRELATION_ID_PATH =
  "inputParameters.startWorkflow.correlationId";

export const StartWorkflowTaskForm = ({ task, onChange }: TaskFormProps) => {
  const authHeaders = useAuthHeaders();

  const maybeSelectedWorkflowName = useMemo(
    () =>
      _isNil(task?.inputParameters?.startWorkflow?.name) ||
      _isEmpty(task?.inputParameters?.startWorkflow?.name)
        ? undefined
        : task?.inputParameters?.startWorkflow?.name,
    [task?.inputParameters?.startWorkflow?.name],
  );

  const handleSelectServiceWhichCallsOnChange = (
    onChange: TaskFormProps["onChange"],
  ) => {
    return async (context: StartSubWfNameVersionMachineContext) => {
      const taskJson = {
        ...task,
        inputParameters: {
          ...task.inputParameters,
          startWorkflow: {
            ...task.inputParameters?.startWorkflow,
            name: context.workflowName,
            version: _last(
              _path(context.workflowName, context.fetchedNamesAndVersions),
            ),
            input: {},
          },
        },
      };
      await updateInputParametersCommon(
        taskJson,
        task,
        authHeaders,
        onChange,
        "inputParameters.startWorkflow",
        "inputParameters.startWorkflow.input",
        TaskType.START_WORKFLOW,
        getWorkflowDefinitionByNameAndVersion,
      );
    };
  };

  const startSubWfNameVersionActor = useInterpret(
    startSubWfNameVersionMachine,
    {
      ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
      context: {
        authHeaders,
        workflowName: maybeSelectedWorkflowName,
      },
      services: {
        handleSelect: handleSelectServiceWhichCallsOnChange(onChange),
      },
    },
  );

  const [
    { wfNameOptions: options, availableVersions, isFetching },
    { handleSelectWorkflowName },
  ] = useStartSubWfNameVersionMachine(startSubWfNameVersionActor);

  const isOpenButtonDisabled = useMemo(
    () =>
      !(
        task?.inputParameters?.startWorkflow?.name &&
        options?.includes(task?.inputParameters?.startWorkflow?.name) &&
        task?.inputParameters?.startWorkflow?.version &&
        !isFetching
      ),
    [
      task?.inputParameters?.startWorkflow?.version,
      isFetching,
      task?.inputParameters?.startWorkflow?.name,
      options,
    ],
  );

  return (
    <Box width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Workflow Parameters"
      >
        <Grid
          container
          sx={{ width: "100%" }}
          spacing={3}
          id="start-workflow-main-form-container"
        >
          <Grid size={12}>
            <Grid container sx={{ width: "100%" }} spacing={3}>
              <Grid
                size={{
                  xs: 12,
                  md: 6,
                }}
              >
                <ConductorAutocompleteVariables
                  id="start-workflow-main-form-workflow-name-field"
                  openOnFocus
                  onChange={(val) => {
                    handleSelectWorkflowName(val);
                  }}
                  onBlur={(val) => {
                    if (task?.inputParameters?.startWorkflow?.name !== val) {
                      handleSelectWorkflowName(val);
                    }
                  }}
                  onInputChange={(val) => {
                    onChange(
                      updateField(
                        "inputParameters.startWorkflow.name",
                        val,
                        task,
                      ),
                    );
                  }}
                  value={task?.inputParameters?.startWorkflow?.name}
                  otherOptions={options}
                  label="Workflow name"
                />
              </Grid>
              <Grid
                size={{
                  xs: 12,
                  md: 4,
                  sm: 12,
                }}
              >
                <ConductorAutocompleteVariables
                  id="start-workflow-main-form-workflow-version-field"
                  openOnFocus
                  onChange={(val) => {
                    const taskJson = {
                      ...task,
                      inputParameters: {
                        ...task.inputParameters,
                        startWorkflow: {
                          ...task.inputParameters?.startWorkflow,
                          version: val,
                        },
                      },
                    };
                    updateInputParametersCommon(
                      taskJson,
                      task,
                      authHeaders,
                      onChange,
                      "inputParameters.startWorkflow",
                      "inputParameters.startWorkflow.input",
                      TaskType.START_WORKFLOW,
                      getWorkflowDefinitionByNameAndVersion,
                    );
                  }}
                  value={task?.inputParameters?.startWorkflow?.version}
                  otherOptions={availableVersions}
                  label="Version"
                  coerceTo="integer"
                />
              </Grid>
              <Grid alignSelf="center">
                <Button
                  id="start-workflow-main-form-workflow-open-btn"
                  disabled={isOpenButtonDisabled}
                  sx={{ fontSize: "12px" }}
                  onClick={() => {
                    openInNewTab(
                      `${WORKFLOW_DEFINITION_URL.BASE}/${
                        encodeURIComponent(
                          task?.inputParameters?.startWorkflow?.name,
                        ) ?? ""
                      }`,
                    );
                  }}
                  startIcon={
                    isFetching && (
                      <CircularProgress id="fetching-icon" size={12} />
                    )
                  }
                >
                  Open
                </Button>
              </Grid>
            </Grid>
          </Grid>
          <Grid size={12}>
            <Grid container sx={{ width: "100%" }} spacing={3}>
              <Grid
                size={{
                  xs: 12,
                  md: 6,
                  sm: 12,
                }}
              >
                <ConductorAutocompleteVariables
                  fullWidth
                  label="Correlation id"
                  value={task?.inputParameters?.startWorkflow.correlationId}
                  onChange={(val) =>
                    onChange(
                      updateField(
                        START_WORKFLOW_CORRELATION_ID_PATH,
                        val,
                        task,
                      ),
                    )
                  }
                />
              </Grid>
            </Grid>
          </Grid>
          <Grid size={12}>
            <Grid container sx={{ width: "100%" }} spacing={3}>
              <IdempotencyForm
                idempotencyValues={{
                  idempotencyKey:
                    task?.inputParameters?.startWorkflow?.idempotencyKey,
                  idempotencyStrategy: task?.inputParameters?.startWorkflow
                    ?.idempotencyStrategy as IdempotencyStrategyEnum,
                }}
                onChange={(data) =>
                  handleChangeIdempotencyValues(
                    data,
                    task,
                    "inputParameters.startWorkflow",
                    onChange,
                  )
                }
              />
            </Grid>
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        title="Input parameters"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <MaybeVariable
          value={task?.inputParameters?.startWorkflow?.input}
          onChange={(val) => {
            onChange(updateField(START_WORKFLOW_INPUT_PATH, val, task));
          }}
          path={"inputParameters.startWorkflow.input"}
          taskType={TaskType.START_WORKFLOW}
        >
          <ConductorFlatMapFormBase
            keyColumnLabel="Key"
            valueColumnLabel="Value"
            addItemLabel="Add params"
            value={task?.inputParameters?.startWorkflow?.input}
            onChange={(val) =>
              onChange(updateField(START_WORKFLOW_INPUT_PATH, val, task))
            }
          />
        </MaybeVariable>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};

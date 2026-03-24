import { Box, CircularProgress, FormControlLabel, Grid } from "@mui/material";
import { useInterpret } from "@xstate/react";
import Button from "components/MuiButton";
import MuiCheckbox from "components/MuiCheckbox";
import { ConductorAutoComplete } from "components/v1/ConductorAutoComplete";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import { path as _path } from "lodash/fp";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import _last from "lodash/last";
import { getWorkflowDefinitionByNameAndVersion } from "pages/definition/commonService";
import IdempotencyForm from "pages/runWorkflow/IdempotencyForm";
import { IdempotencyStrategyEnum } from "pages/runWorkflow/types";
import { useMemo } from "react";
import { TaskType } from "types";
import { WORKFLOW_DEFINITION_URL } from "utils/constants/route";
import { updateField } from "utils/fieldHelpers";
import { useAuthHeaders } from "utils/query";
import {
  handleChangeIdempotencyValues,
  updateInputParametersCommon,
} from "../../helpers";
import { ConductorObjectOrStringInput } from "../ConductorObjectOrStringInput";
import { Optional } from "../OptionalFieldForm";
import {
  StartSubWfNameVersionMachineContext,
  startSubWfNameVersionMachine,
} from "../StartWorkflowTaskForm/state";
import { useStartSubWfNameVersionMachine } from "../StartWorkflowTaskForm/state/hook";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";

const SUB_WORKFLOW_INPUT_PARAMETER_PATH = "inputParameters";
const SUB_WORKFLOW_TASK_TO_DOMAIN_PATH = "subWorkflowParam.taskToDomain";

export const SubWorkflowOperatorForm = ({
  task,
  onChange,
  onToggleExpand,
  collapseWorkflowList,
}: TaskFormProps) => {
  const authHeaders = useAuthHeaders();

  const maybeSelectedWorkflowName = useMemo(
    () =>
      _isNil(task?.subWorkflowParam?.name) ||
      _isEmpty(task?.subWorkflowParam?.name)
        ? undefined
        : task?.subWorkflowParam?.name,
    [task?.subWorkflowParam?.name],
  );

  const handleSelectServiceWhichCallsOnChange = (
    onChange: TaskFormProps["onChange"],
  ) => {
    return async (context: StartSubWfNameVersionMachineContext) => {
      const taskJson = {
        ...task,
        inputParameters: {},
        subWorkflowParam: {
          ...task.subWorkflowParam,
          name: context.workflowName,
          version: _last(
            _path(context.workflowName, context.fetchedNamesAndVersions),
          ) as number,
        },
      };

      await updateInputParametersCommon(
        taskJson,
        task,
        authHeaders,
        onChange,
        "subWorkflowParam",
        "inputParameters",
        TaskType.SUB_WORKFLOW,
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
        task?.subWorkflowParam?.name &&
        options?.includes(task?.subWorkflowParam?.name) &&
        task?.subWorkflowParam?.version &&
        !isFetching
      ),
    [
      task?.subWorkflowParam?.name,
      options,
      task?.subWorkflowParam?.version,
      isFetching,
    ],
  );

  const isPriorityError =
    typeof task?.subWorkflowParam?.priority === "number" &&
    (task.subWorkflowParam.priority < 0 || task.subWorkflowParam.priority > 99);
  return (
    <Box width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Sub Workflow Parameters"
      >
        <Grid
          container
          sx={{ width: "100%" }}
          spacing={2}
          id="sub-workflow-main-form-container"
        >
          <Grid size={12}>
            <Grid container sx={{ width: "100%" }} spacing={2}>
              <Grid
                size={{
                  xs: 12,
                  md: 6,
                }}
              >
                <ConductorAutocompleteVariables
                  id="sub-workflow-main-form-workflow-name-field"
                  openOnFocus
                  onChange={(val) => {
                    handleSelectWorkflowName(val);
                  }}
                  onBlur={(val) => {
                    if (task.subWorkflowParam?.name !== val) {
                      handleSelectWorkflowName(val);
                    }
                  }}
                  onInputChange={(val) => {
                    onChange(updateField("subWorkflowParam.name", val, task));
                  }}
                  value={task.subWorkflowParam?.name}
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
                <ConductorAutoComplete
                  id="sub-workflow-main-form-workflow-version-field"
                  fullWidth
                  onChange={(_, val) => {
                    // Convert the value to an integer if it's not already
                    const version =
                      typeof val === "string" ? parseInt(val, 10) : val;

                    const taskJson = {
                      ...task,
                      subWorkflowParam: {
                        ...task.subWorkflowParam,
                        version,
                      },
                    };
                    updateInputParametersCommon(
                      taskJson,
                      task,
                      authHeaders,
                      onChange,
                      "subWorkflowParam",
                      "inputParameters",
                      TaskType.SUB_WORKFLOW,
                      getWorkflowDefinitionByNameAndVersion,
                    );
                  }}
                  value={task.subWorkflowParam?.version}
                  options={availableVersions}
                  label="Version"
                />
              </Grid>
              <Grid alignSelf="center">
                <Button
                  id="sub-workflow-main-form-workflow-open-btn"
                  disabled={isOpenButtonDisabled}
                  sx={{ fontSize: "12px" }}
                  onClick={() => {
                    window.open(
                      `${WORKFLOW_DEFINITION_URL.BASE}/${encodeURIComponent(
                        task?.subWorkflowParam?.name ?? "",
                      )}`,
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
            <Grid size={12} pt={3}>
              <Grid container sx={{ width: "100%" }} spacing={2}>
                <Grid size={{ sm: 12, md: 9 }}>
                  <ConductorObjectOrStringInput
                    valueLabel="Workflow definition"
                    value={task?.subWorkflowParam?.workflowDefinition ?? ""}
                    onChangeValue={(val) => {
                      onChange(
                        updateField(
                          "subWorkflowParam.workflowDefinition",
                          val,
                          task,
                        ),
                      );
                    }}
                  />
                </Grid>
                <Grid
                  size={{
                    md: 3,
                    sm: 6,
                  }}
                >
                  <ConductorAutocompleteVariables
                    id=""
                    fullWidth
                    label="Priority"
                    value={task?.subWorkflowParam?.priority ?? ""}
                    onChange={(val) =>
                      onChange(
                        updateField("subWorkflowParam.priority", val, task),
                      )
                    }
                    error={isPriorityError}
                    helperText={
                      isPriorityError ? "must be from 0 to 99" : undefined
                    }
                    coerceTo="integer"
                    inputProps={{
                      tooltip: {
                        title: "Priority",
                        content:
                          "If set, this priority overrides the parent workflow’s priority. If not, it inherits the parent workflow’s priority.",
                      },
                    }}
                  />
                </Grid>
              </Grid>
            </Grid>
            <Grid size={12} pt={3}>
              <Grid container spacing={3}>
                <IdempotencyForm
                  showStrategyInitially
                  idempotencyValues={{
                    idempotencyKey: task?.subWorkflowParam?.idempotencyKey,
                    idempotencyStrategy: task?.subWorkflowParam
                      ?.idempotencyStrategy as IdempotencyStrategyEnum,
                  }}
                  onChange={(data) =>
                    handleChangeIdempotencyValues(
                      data,
                      task,
                      "subWorkflowParam",
                      onChange,
                    )
                  }
                />
              </Grid>
            </Grid>
            <Grid container sx={{ width: "100%" }} spacing={2}>
              <Grid>
                <FormControlLabel
                  control={
                    <MuiCheckbox
                      checked={
                        task &&
                        task?.subWorkflowParam &&
                        task?.subWorkflowParam?.name &&
                        collapseWorkflowList?.includes(
                          task?.subWorkflowParam?.name,
                        )
                          ? true
                          : false
                      }
                      onChange={() =>
                        onToggleExpand(task?.subWorkflowParam?.name)
                      }
                    />
                  }
                  label="Expand"
                />
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        title="Input parameters"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <ConductorFlatMapFormBase
          showFieldTypes={true}
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add input parameter"
          hiddenKeys={["evaluatorType", "expression"]}
          value={_path(SUB_WORKFLOW_INPUT_PARAMETER_PATH, task)}
          onChange={(value) =>
            onChange(
              updateField(SUB_WORKFLOW_INPUT_PARAMETER_PATH, value, task),
            )
          }
        />
      </TaskFormSection>
      <TaskFormSection
        title="Task to domain"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <ConductorFlatMapFormBase
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add mapping"
          value={_path(SUB_WORKFLOW_TASK_TO_DOMAIN_PATH, task)}
          onChange={(value) =>
            onChange(updateField(SUB_WORKFLOW_TASK_TO_DOMAIN_PATH, value, task))
          }
        />
      </TaskFormSection>

      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};

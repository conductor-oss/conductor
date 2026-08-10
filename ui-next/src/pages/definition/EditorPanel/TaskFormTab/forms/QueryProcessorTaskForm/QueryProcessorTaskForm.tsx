import { Box, Grid } from "@mui/material";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { path as _path } from "lodash/fp";
import { WorkflowExecutionStatus } from "types/Execution";
import { QueryProcessorType } from "types/TaskType";
import { updateField } from "utils/fieldHelpers";
import { useWorkflowNames } from "utils/query";
import { ConductorValueInput } from "../ConductorValueInput";
import { useTaskForm } from "../hooks/useTaskForm";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";
import { MetricsTypeForm } from "./MetricsTypeForm";

const DEFAULT_VALUES_FOR_ARRAY = { object: [] };
const statusOptions = Object.values(WorkflowExecutionStatus);
const queryProcessorTypePath = "inputParameters.queryType";
const workflowNamesPath = "inputParameters.workflowNames";
const correlationIdsPath = "inputParameters.correlationIds";
const statusesPath = "inputParameters.statuses";
const freeTextPath = "inputParameters.freeText";
const startTimeFromPath = "inputParameters.startTimeFrom";
const startTimeToPath = "inputParameters.startTimeTo";
const endTimeFromPath = "inputParameters.endTimeFrom";
const endTimeToPath = "inputParameters.endTimeTo";

export const QueryProcessorTaskForm = ({ task, onChange }: TaskFormProps) => {
  const [queryType, setQueryType] = useTaskForm(queryProcessorTypePath, {
    task,
    onChange,
  });
  const workflowNames = _path(workflowNamesPath, task);
  const correlationIds = _path(correlationIdsPath, task);
  const statuses = _path(statusesPath, task);
  const freeText = _path(freeTextPath, task);
  const startTimeFrom = _path(startTimeFromPath, task);
  const startTimeTo = _path(startTimeToPath, task);
  const endTimeFrom = _path(endTimeFromPath, task);
  const endTimeTo = _path(endTimeToPath, task);

  const workflowNamesOptions: string[] = useWorkflowNames();

  const changeWorkflowNames = (value: string) => {
    onChange(updateField(workflowNamesPath, value, task));
  };
  const changeCorrelationIds = (value: string) => {
    onChange(updateField(correlationIdsPath, value, task));
  };
  const changeStatuses = (value: string | string[]) => {
    onChange(updateField(statusesPath, value, task));
  };
  const changeFreeText = (value: string) => {
    onChange(updateField(freeTextPath, value, task));
  };
  const changeStartTimeFrom = (value: any) => {
    onChange(updateField(startTimeFromPath, value, task));
  };
  const changeStartTimeTo = (value: any) => {
    onChange(updateField(startTimeToPath, value, task));
  };

  const changeEndTimeFrom = (value: any) => {
    onChange(updateField(endTimeFromPath, value, task));
  };
  const changeEndTimeTo = (value: any) => {
    onChange(updateField(endTimeToPath, value, task));
  };

  const changeTemplateType = (type: string) => {
    setQueryType(type);
    const conductorApiTemplate = {
      workflowNames: [],
      statuses: [],
      correlationIds: [],
    };
    const metricsTemplate = {
      metricsQuery: "",
      metricsStart: "",
      metricsEnd: "",
      metricsStep: "",
    };
    if (type === QueryProcessorType.CONDUCTOR_API) {
      onChange({
        ...task,
        inputParameters: {
          ...conductorApiTemplate,
          queryType: type,
        },
      });
    } else if (type === QueryProcessorType.METRICS) {
      onChange({
        ...task,
        inputParameters: {
          ...metricsTemplate,
          queryType: type,
        },
      });
    }
  };

  return (
    <Box>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Query Type"
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <RadioButtonGroup
              items={[
                {
                  value: QueryProcessorType.CONDUCTOR_API,
                  label: "Conductor Search API",
                },
                {
                  value: QueryProcessorType.METRICS,
                  label: "Conductor Metrics (Prometheus)",
                },
                {
                  value: QueryProcessorType.CONDUCTOR_EVENTS,
                  label: "Conductor Events",
                  disabled: true,
                },
              ]}
              name="queryProcessorType"
              value={queryType}
              onChange={(event, value) => {
                changeTemplateType(value);
              }}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        title="Query"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        {queryType === QueryProcessorType.CONDUCTOR_API && (
          <Grid container sx={{ width: "100%" }} spacing={3} mt={1}>
            <Grid size={12}>
              <ConductorValueInput
                valueLabel="Workflow names"
                value={workflowNames}
                onChangeValue={(val: string) => {
                  changeWorkflowNames(val);
                }}
                dropDownOptions={workflowNamesOptions}
                defaultObjectValue={DEFAULT_VALUES_FOR_ARRAY}
              />
            </Grid>
            <Grid size={12}>
              <ConductorValueInput
                valueLabel="Correlation ids"
                value={correlationIds}
                onChangeValue={(val: string) => {
                  changeCorrelationIds(val);
                }}
                defaultObjectValue={DEFAULT_VALUES_FOR_ARRAY}
              />
            </Grid>
            <Grid size={12}>
              <ConductorValueInput
                valueLabel="Statuses"
                value={statuses}
                dropDownOptions={statusOptions}
                onChangeValue={(val: string) => {
                  changeStatuses(val);
                }}
                defaultObjectValue={DEFAULT_VALUES_FOR_ARRAY}
              />
            </Grid>
            <Grid size={12}>
              <Grid
                container
                sx={{ width: "100%" }}
                alignItems={"center"}
                gap={2}
              >
                <Grid flexGrow={1}>
                  <ConductorAutocompleteVariables
                    label="Start time from (Now -"
                    value={startTimeFrom}
                    fullWidth
                    onChange={changeStartTimeFrom}
                    coerceTo="integer"
                    growPopper
                  />
                </Grid>
                <Grid flexGrow={1}>
                  <ConductorAutocompleteVariables
                    label={`mins) to (Now -`}
                    value={startTimeTo}
                    fullWidth
                    onChange={changeStartTimeTo}
                    coerceTo="integer"
                    growPopper
                  />
                </Grid>
              </Grid>
            </Grid>
            <Grid size={12}>
              <Grid
                container
                sx={{ width: "100%" }}
                alignItems={"center"}
                gap={2}
              >
                <Grid flexGrow={1}>
                  <ConductorAutocompleteVariables
                    label={"End time from (Now - "}
                    value={endTimeFrom}
                    fullWidth
                    onChange={changeEndTimeFrom}
                    coerceTo="integer"
                    growPopper
                  />
                </Grid>
                <Grid flexGrow={1}>
                  <ConductorAutocompleteVariables
                    label={`mins) to (Now -`}
                    value={endTimeTo}
                    fullWidth
                    onChange={changeEndTimeTo}
                    coerceTo="integer"
                    growPopper
                  />
                </Grid>
              </Grid>
            </Grid>
            <Grid size={12}>
              <ConductorInput
                fullWidth
                label="Free text search"
                value={freeText}
                onTextInputChange={changeFreeText}
              />
            </Grid>
          </Grid>
        )}
        {queryType === QueryProcessorType.METRICS && (
          <MetricsTypeForm task={task} onChange={onChange} />
        )}
      </TaskFormSection>
    </Box>
  );
};

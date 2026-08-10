import {
  Box,
  FormControlLabel,
  Grid,
  GridProps,
  Link,
  Switch,
} from "@mui/material";
import MuiTypography from "components/ui/MuiTypography";
import { ConductorArrayFieldBase } from "components/ui/inputs/ConductorArrayField";
import ConductorInput from "components/ui/inputs/ConductorInput";
import ConductorInputNumber from "components/ui/inputs/ConductorInputNumber";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import _ from "lodash";
import _isArray from "lodash/isArray";
import { useTaskDefinitionFormActor } from "pages/definition/task/form/state/hook";
import {
  TaskDefinitionFormProps,
  TaskRetryLogic,
  TaskRetryLogicLabel,
  TaskTimeoutPolicy,
  TaskTimeoutPolicyLabel,
} from "pages/definition/task/state";
import { ConductorNameVersionField } from "components/inputs/ConductorNameVersionField";
import { SchemaDefinition } from "types/SchemaDefinition";
import { handleValidChars } from "utils";
import { TASK_NAME_REGEX, regexToString } from "utils/constants/regex";

const gridContainerItemProps: GridProps = {
  container: true,
  size: {
    xs: 12,
    sm: 12,
    md: 6,
  },
  spacing: 3,
  width: "100%",
};

const forceToArrayIfWrongType = (val: any) => (_isArray(val) ? val : []);

const TaskDefinitionForm = ({ formActor }: TaskDefinitionFormProps) => {
  const [
    { modifiedTaskDefinition, error },
    { handleChangeTaskForm, handleChangeParameters, handleChangeInputForm },
  ] = useTaskDefinitionFormActor(formActor);

  const isLinearBackoff =
    modifiedTaskDefinition.retryLogic === TaskRetryLogic.LINEAR_BACKOFF;
  const isBackoff =
    modifiedTaskDefinition.retryLogic === TaskRetryLogic.EXPONENTIAL_BACKOFF ||
    isLinearBackoff;

  return (
    <Grid
      id="task-form-container"
      container
      spacing={4}
      pl={5}
      pt={3}
      pr={8}
      pb={5}
      sx={{ width: "100%" }}
    >
      <Grid {...gridContainerItemProps}>
        <Grid size={12} flexGrow={1}>
          <Grid container spacing={3}>
            <Grid size={12}>
              <MuiTypography fontWeight={800} fontSize={16}>
                Basic settings
              </MuiTypography>
            </Grid>
            <Grid size={12} id="task-form-name-container">
              <ConductorInput
                label="Name"
                name="name"
                required
                fullWidth
                onTextInputChange={handleValidChars(
                  (value) => handleChangeInputForm("name", value),
                  regexToString(TASK_NAME_REGEX),
                )}
                value={modifiedTaskDefinition.name}
                error={!!error?.name}
                helperText={error?.name?.message}
                id="task-name-field"
                placeholder="Enter task name"
              />
            </Grid>
            <Grid size={12}>
              <ConductorInput
                id="task-description-field"
                label="Description"
                name="description"
                multiline
                minRows={3.75}
                fullWidth
                onTextInputChange={(value) =>
                  handleChangeInputForm("description", value)
                }
                value={modifiedTaskDefinition.description}
                error={
                  !!error?.description || !modifiedTaskDefinition.description
                }
                helperText={error?.description?.message}
                required
                autoFocus
                placeholder="Enter description"
                sx={{
                  "& .MuiInputBase-root": {
                    alignItems: "flex-start",
                  },
                }}
              />
            </Grid>
          </Grid>
        </Grid>
      </Grid>
      <Grid {...gridContainerItemProps}>
        <Grid>
          <Box>
            <Grid container sx={{ width: "100%" }} spacing={3}>
              <Grid size={12}>
                <MuiTypography fontWeight={800} fontSize={16}>
                  Rate limit settings
                </MuiTypography>
              </Grid>
              <Grid size={12}>
                <ConductorInputNumber
                  id="task-rateLimitPerFrequency-field"
                  fullWidth
                  label="Rate limit per frequency"
                  name="rateLimitPerFrequency"
                  onChange={handleChangeTaskForm}
                  value={modifiedTaskDefinition.rateLimitPerFrequency}
                  error={!!error?.rateLimitPerFrequency}
                  helperText={error?.rateLimitPerFrequency?.message}
                  inputProps={{
                    allowNegative: false,
                  }}
                  placeholder="0"
                  tooltip={{
                    title: "Rate limit per frequency",
                    content:
                      "The number of task executions given to workers per frequency window.",
                  }}
                />
              </Grid>
              <Grid size={12}>
                <ConductorInputNumber
                  id="task-rateLimitFrequencyInSeconds-field"
                  fullWidth
                  label="Frequency seconds"
                  name="rateLimitFrequencyInSeconds"
                  onChange={handleChangeTaskForm}
                  value={modifiedTaskDefinition.rateLimitFrequencyInSeconds}
                  error={!!error?.rateLimitFrequencyInSeconds}
                  helperText={error?.rateLimitFrequencyInSeconds?.message}
                  inputProps={{
                    allowNegative: false,
                  }}
                  placeholder="1"
                  tooltip={{
                    title: "Frequency seconds",
                    content: "The duration of the frequency window in seconds.",
                  }}
                />
              </Grid>
              <Grid size={12}>
                <ConductorInputNumber
                  id="task-concurrentExecLimit-field"
                  label="Concurrent execution limit"
                  fullWidth
                  name="concurrentExecLimit"
                  onChange={handleChangeTaskForm}
                  value={modifiedTaskDefinition.concurrentExecLimit}
                  error={!!error?.concurrentExecLimit}
                  helperText={error?.concurrentExecLimit?.message}
                  inputProps={{
                    allowNegative: false,
                  }}
                  placeholder="0"
                  tooltip={{
                    title: "Concurrent execution limit",
                    content:
                      "The number of task executions that can be executed concurrently.",
                  }}
                />
              </Grid>
            </Grid>
          </Box>
        </Grid>
      </Grid>
      <Grid {...gridContainerItemProps}>
        <Grid size={12}>
          <MuiTypography fontWeight={800} fontSize={16}>
            Retry settings
          </MuiTypography>
        </Grid>
        <Grid size={12}>
          <ConductorInputNumber
            id="task-retryCount-field"
            label="No. of times to retry the task upon failure?"
            name="retryCount"
            fullWidth
            onChange={handleChangeTaskForm}
            value={modifiedTaskDefinition.retryCount}
            error={!!error?.retryCount}
            helperText={error?.retryCount?.message}
            inputProps={{
              allowNegative: false,
            }}
            placeholder="3"
          />
        </Grid>
        <Grid size={12}>
          <ConductorInputNumber
            id="task-retryDelaySeconds-field"
            label="Delay between retries in seconds"
            fullWidth
            name="retryDelaySeconds"
            onChange={handleChangeTaskForm}
            value={modifiedTaskDefinition.retryDelaySeconds}
            error={!!error?.retryDelaySeconds}
            helperText={error?.retryDelaySeconds?.message}
            inputProps={{
              allowNegative: false,
            }}
            tooltip={{
              title: "Delay between retries in seconds",
              content: "The time (in seconds) to wait before each retry.",
            }}
            placeholder="60"
          />
        </Grid>
        <Grid size={12}>
          <ConductorSelect
            id="task-retryLogic-field"
            label="Retry policy"
            name="retryLogic"
            fullWidth
            onChange={(event: any) =>
              handleChangeTaskForm(event.target.value, event)
            }
            value={modifiedTaskDefinition.retryLogic}
            tooltip={{
              title: "Retry policy",
              content: "The mechanism for retries.",
            }}
            items={Object.values(TaskRetryLogic).map((value) => ({
              label: TaskRetryLogicLabel[value],
              value,
            }))}
          />
        </Grid>
        <Grid size={12}>
          <ConductorInputNumber
            id="task-backoffScaleFactor-field"
            label="Backoff scale factor"
            fullWidth
            name="backoffScaleFactor"
            onChange={handleChangeTaskForm}
            value={modifiedTaskDefinition.backoffScaleFactor}
            error={!!error?.backoffScaleFactor}
            helperText={error?.backoffScaleFactor?.message}
            inputProps={{
              allowNegative: false,
            }}
            tooltip={{
              title: "Backoff scale factor",
              content:
                "The value multiplied with retryDelaySeconds to determine the interval for retry.",
            }}
            disabled={!isBackoff}
            placeholder="0"
          />
        </Grid>
      </Grid>
      <Grid {...gridContainerItemProps}>
        <Grid size={12}>
          <MuiTypography fontWeight={800} fontSize={16}>
            Timeout settings
          </MuiTypography>
        </Grid>
        <Grid size={12}>
          <ConductorInputNumber
            id="task-responseTimeoutSeconds-field"
            label="Response Timeout Seconds"
            fullWidth
            name="responseTimeoutSeconds"
            onChange={handleChangeTaskForm}
            value={modifiedTaskDefinition.responseTimeoutSeconds}
            error={!!error?.responseTimeoutSeconds}
            helperText={error?.responseTimeoutSeconds?.message}
            inputProps={{
              allowNegative: false,
            }}
            tooltip={{
              title: "Task response timeout",
              content:
                "The maximum duration in seconds that a worker has to respond to the server with a status update before it gets marked as TIMED_OUT.",
            }}
            placeholder="3600"
          />
        </Grid>
        <Grid size={12}>
          <ConductorInputNumber
            id="task-timeoutSeconds-field"
            fullWidth
            label="Timeout Seconds"
            name="timeoutSeconds"
            onChange={handleChangeTaskForm}
            value={modifiedTaskDefinition.timeoutSeconds}
            error={!!error?.timeoutSeconds}
            helperText={error?.timeoutSeconds?.message}
            inputProps={{
              allowNegative: false,
            }}
            tooltip={{
              title: "Timeout seconds",
              content:
                "Time (in seconds) for the task to reach a terminal state before it gets marked as TIMED_OUT. No timeout if set to 0.",
            }}
            placeholder="3600"
          />
        </Grid>
        <Grid size={12}>
          <ConductorInputNumber
            id="task-pollTimeoutSeconds-field"
            label="Poll Timeout Seconds"
            fullWidth
            name="pollTimeoutSeconds"
            onChange={handleChangeTaskForm}
            value={modifiedTaskDefinition.pollTimeoutSeconds}
            error={!!error?.pollTimeoutSeconds}
            helperText={error?.pollTimeoutSeconds?.message}
            inputProps={{
              allowNegative: false,
            }}
            tooltip={{
              title: "Poll Timeout Seconds",
              content:
                "Time (in seconds), after which the task is marked as TIMED_OUT if not picked by a worker. No timeout if set to 0.",
            }}
            placeholder="3600"
          />
        </Grid>
        <Grid size={12}>
          <ConductorSelect
            id="task-timeoutPolicy-field"
            label="Timeout policy"
            name="timeoutPolicy"
            onChange={(event: any) =>
              handleChangeTaskForm(event.target.value, event)
            }
            value={modifiedTaskDefinition.timeoutPolicy}
            fullWidth
            tooltip={{
              title: "Timeout policy",
              content: "The policy for handling timeout",
            }}
            items={Object.values(TaskTimeoutPolicy).map((value) => ({
              label: TaskTimeoutPolicyLabel[value],
              value,
            }))}
          />
        </Grid>
      </Grid>
      <Grid container sx={{ width: "100%" }} spacing={2}>
        <Grid size={12}>
          <MuiTypography fontWeight={800} fontSize={16}>
            Schema
          </MuiTypography>
          <MuiTypography>
            JSON schema for the input/output validation.{" "}
            <Link
              sx={{ fontWeight: 400 }}
              target="_blank"
              href={`https://orkes.io/content/developer-guides/schema-validation`}
              rel="noreferrer"
            >
              Learn more.
            </Link>
          </MuiTypography>
        </Grid>

        <Grid
          size={{
            xs: 12,
            lg: 12,
          }}
        >
          <Box
            sx={{
              marginTop: "10px",
            }}
          >
            <ConductorNameVersionField
              label="Input Schema"
              optionsUrl="/schema"
              versionField={{
                emptyText: "Latest Version",
              }}
              mapOptions={(data: SchemaDefinition[]) =>
                _.chain(data)
                  .groupBy("name")
                  .map((group, key) => ({
                    name: key,
                    versions: _.map(group, "version"),
                  }))
                  .value()
              }
              value={modifiedTaskDefinition.inputSchema}
              onChange={(value) => {
                if (value) {
                  handleChangeInputForm("inputSchema", {
                    ...value,
                    type: "JSON",
                  });
                } else {
                  handleChangeInputForm("inputSchema", undefined);
                }
              }}
            />
          </Box>

          <Box
            sx={{
              marginTop: "20px",
            }}
          >
            <ConductorNameVersionField
              label="Output Schema"
              optionsUrl="/schema"
              versionField={{
                emptyText: "Latest Version",
              }}
              mapOptions={(data: SchemaDefinition[]) =>
                _.chain(data)
                  .groupBy("name")
                  .map((group, key) => ({
                    name: key,
                    versions: _.map(group, "version"),
                  }))
                  .value()
              }
              value={modifiedTaskDefinition.outputSchema}
              onChange={(value) => {
                if (value) {
                  handleChangeInputForm("outputSchema", {
                    ...value,
                    type: "JSON",
                  });
                } else {
                  handleChangeInputForm("outputSchema", undefined);
                }
              }}
            />
          </Box>

          <Box
            sx={{
              marginTop: "20px",
            }}
          >
            <FormControlLabel
              checked={modifiedTaskDefinition.enforceSchema}
              control={
                <Switch
                  color="primary"
                  style={{ marginRight: 8 }}
                  onChange={({ target: { checked } }) =>
                    handleChangeInputForm("enforceSchema", checked)
                  }
                />
              }
              label="Enforce schema"
            />
          </Box>
        </Grid>
      </Grid>
      <Grid container sx={{ width: "100%" }} spacing={2}>
        <Grid size={12}>
          <MuiTypography fontWeight={800} fontSize={16}>
            Task input template:
          </MuiTypography>
          <MuiTypography>
            These values act as the task's default input when added to the
            workflow and can be overridden within a workflow.{" "}
            <Link
              sx={{ fontWeight: 400 }}
              target="_blank"
              href={`https://orkes.io/content/developer-guides/task-input-templates`}
              rel="noreferrer"
            >
              Learn more.
            </Link>
          </MuiTypography>
        </Grid>

        <Grid
          size={{
            xs: 12,
          }}
        >
          <Box>
            <ConductorFlatMapFormBase
              onChange={(newValues) => {
                handleChangeParameters({
                  name: "inputTemplate",
                  value: newValues,
                });
              }}
              value={{ ...modifiedTaskDefinition.inputTemplate }}
              title=""
              typeColumnLabel="Type"
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              showFieldTypes
              enableAutocomplete={false}
            />
          </Box>
        </Grid>
      </Grid>
      <Grid container sx={{ width: "100%" }} spacing={2}>
        <Grid size={12}>
          <MuiTypography fontWeight={800} fontSize={16}>
            Input keys:
          </MuiTypography>
          <MuiTypography>
            These values serve as an indicator of the expected input for the
            task.
          </MuiTypography>
        </Grid>

        <Grid size={12}>
          <ConductorArrayFieldBase
            onChange={(newValues) => {
              handleChangeParameters({
                name: "inputKeys",
                value: newValues,
              });
            }}
            value={forceToArrayIfWrongType(modifiedTaskDefinition.inputKeys)}
            inputLabel="Key"
            placeholder="e.g: some key..."
            addButtonLabel="Add key"
          />
        </Grid>
      </Grid>
      <Grid container sx={{ width: "100%" }} spacing={2}>
        <Grid size={12}>
          <MuiTypography fontWeight={800} fontSize={16}>
            Output keys:
          </MuiTypography>
          <MuiTypography>
            These values serve as an indicator of the expected output from the
            task.
          </MuiTypography>
        </Grid>

        <Grid size={12}>
          <ConductorArrayFieldBase
            onChange={(newValues) => {
              handleChangeParameters({
                name: "outputKeys",
                value: newValues,
              });
            }}
            value={forceToArrayIfWrongType(modifiedTaskDefinition.outputKeys)}
            inputLabel="Key"
            placeholder="e.g: some key..."
            addButtonLabel="Add key"
          />
        </Grid>
      </Grid>
    </Grid>
  );
};

export default TaskDefinitionForm;

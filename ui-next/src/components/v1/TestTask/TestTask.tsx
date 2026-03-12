import { RocketLaunch } from "@mui/icons-material";
import {
  Box,
  FormControlLabel,
  IconButton,
  Link,
  Radio,
  RadioGroup,
  Tooltip,
  TooltipProps,
  styled,
} from "@mui/material";
import CircularProgress from "@mui/material/CircularProgress";
import MuiButton from "components/MuiButton";
import MuiTypography from "components/MuiTypography";
import WorkflowStatusBadge from "components/WorkflowStatusBadge";
import _assoc from "lodash/fp/assoc";
import { ChangeEvent, FunctionComponent, useMemo, useState } from "react";
import {
  FormSectionProps,
  JsonSectionProps,
  TestControlsProps,
  TestOutputProps,
  TestTaskProps,
} from "types/TestTaskTypes";
import { extractVariablesFromJSON } from "utils/json";
import { tryToJson } from "utils/utils";
import { ConductorCodeBlockInput } from "../ConductorCodeBlockInput";
import ConductorInput from "../ConductorInput";
import XCloseIcon from "../icons/XCloseIcon";

const CustomisedTooltip = styled(({ className, ...props }: TooltipProps) => (
  <Tooltip {...props} classes={{ popper: className }} />
))(() => ({
  "& .MuiTooltip-tooltip": {
    backgroundColor: "white",
    color: "rgba(6, 6, 6, 1)",
    minWidth: 450,
    width: "100%",
    filter: "drop-shadow(0px 0px 6px rgba(89, 89, 89, 0.41))",
    borderRadius: "6px",
    padding: "15px 10px 10px 15px",
    border: "1px solid #0D94DB",
  },
  "& .MuiTooltip-arrow": {
    color: "white",
    fontSize: "28px",
    "&:before": {
      border: "1px solid #0D94DB",
    },
  },
}));

const closeIconStyle = {
  position: "absolute",
  right: 0,
  cursor: "pointer",
};

const FormSection: FunctionComponent<FormSectionProps> = ({
  extractedJsonVariables,
  value,
  onChangeModel,
  domain,
  onChangeDomain,
}) => {
  const handleVariableChange = (val: string, newValue: string) => {
    const keysWithValue = Object.keys(extractedJsonVariables).filter(
      (key) => extractedJsonVariables[key] === val,
    );
    const updatedJsonValue = keysWithValue.reduce(
      (acc, key) => _assoc(key, newValue, acc),
      { ...value },
    );
    onChangeModel(updatedJsonValue);
  };

  const uniqueValues = Array.from(
    new Set(Object.values(extractedJsonVariables)),
  ) as string[];

  return (
    <Box>
      {uniqueValues.map((val) => {
        const keyForTargetValue =
          Object.keys(extractedJsonVariables).find(
            (key) => extractedJsonVariables[key] === val,
          ) || "";
        return (
          <Box key={val} sx={{ padding: "10px 0 0 0" }}>
            <ConductorInput
              id={`variable-substitue-for-${val?.replaceAll(".", "-")}`}
              label={`Variable substitute for \${${val}}`}
              required
              fullWidth
              value={
                value?.[keyForTargetValue] === `$\{${val}}`
                  ? ""
                  : value?.[keyForTargetValue]
              }
              onChange={(event) =>
                handleVariableChange(val, event.target.value)
              }
            />
          </Box>
        );
      })}
      <Box sx={{ padding: "10px 0 20px 0" }}>
        <ConductorInput
          label="Domain"
          fullWidth
          value={domain ?? ""}
          onChange={(event) => onChangeDomain(event.target.value)}
          data-testid="test-task-domain-input"
        />
      </Box>
    </Box>
  );
};

const JsonSection: FunctionComponent<JsonSectionProps> = ({
  handleJSONChange,
  taskModel,
  value,
  domain,
  onChangeDomain,
}) => {
  return (
    <Box>
      <ConductorCodeBlockInput
        label="Input Parameters"
        onChange={(val) => handleJSONChange(val)}
        value={JSON.stringify({ ...taskModel, ...value }, null, 2)}
        containerStyles={{ borderColor: "#ffffff" }}
        minHeight={150}
        options={{
          lineNumbers: "off",
        }}
      />
      <Box sx={{ padding: "20px 0" }}>
        <ConductorInput
          label="Domain"
          fullWidth
          value={domain ?? ""}
          onChange={(event) => onChangeDomain(event.target.value)}
          data-testid="test-task-domain-input"
        />
      </Box>
    </Box>
  );
};

const TestControls: FunctionComponent<TestControlsProps> = ({
  taskModel,
  value,
  isInProgress,
  handleRunTestTask,
  onChangeModel,
  domain,
  onChangeDomain,
  showForm,
}) => {
  const [toggleValue, setToggleValue] = useState(showForm ? "form" : "json");
  const extractedJsonVariables = extractVariablesFromJSON(taskModel);

  const handleToggleChange = (e: ChangeEvent<HTMLInputElement>) => {
    setToggleValue(e.target.value);
  };

  const handleJSONChange = (newValue: string) => {
    const parsedObject = tryToJson(newValue);

    if (parsedObject) {
      onChangeModel(parsedObject as Record<string, unknown>);
    }
  };

  return (
    <Box sx={{ padding: "0 20px" }}>
      <Box>
        <FormControlLabel
          labelPlacement="start"
          control={
            <RadioGroup
              row
              title="Provide the task inputs via:"
              name="row-radio-buttons-group"
              value={toggleValue}
              onChange={handleToggleChange}
              sx={{ marginLeft: "10px" }}
            >
              {showForm && (
                <FormControlLabel
                  value="form"
                  control={<Radio />}
                  label="Form"
                />
              )}
              <FormControlLabel value="json" control={<Radio />} label="JSON" />
            </RadioGroup>
          }
          label="Provide the task inputs via:"
          sx={{
            marginLeft: 0,
            marginBottom: 2,
            "& .MuiFormControlLabel-label": {
              color: "#858585",
              fontWeight: 500,
              fontSize: 12,
            },
            "& .MuiFormControlLabel-root .MuiFormControlLabel-label": {
              fontWeight: 300,
            },
          }}
        />
        {toggleValue === "json" ? (
          <JsonSection
            handleJSONChange={handleJSONChange}
            taskModel={taskModel}
            value={value}
            domain={domain}
            onChangeDomain={onChangeDomain}
          />
        ) : (
          <FormSection
            extractedJsonVariables={extractedJsonVariables}
            value={value}
            onChangeModel={onChangeModel}
            domain={domain}
            onChangeDomain={onChangeDomain}
          />
        )}
      </Box>
      <Box display="flex" justifyContent="flex-end">
        <MuiButton
          startIcon={<RocketLaunch />}
          color="primary"
          id="run-test-task"
          disabled={isInProgress}
          onClick={handleRunTestTask}
          sx={{
            "& .MuiButton-startIcon": {
              margin: 0,
            },
          }}
        >
          Run Test
        </MuiButton>
      </Box>
    </Box>
  );
};

const InProgressState: FunctionComponent = () => {
  return (
    <Box
      sx={{
        padding: "100px 0",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
      }}
    >
      <MuiTypography sx={{ fontSize: "20px" }}>Running...</MuiTypography>
      <CircularProgress
        color="inherit"
        size="20px"
        sx={{ marginLeft: "10px" }}
      />
    </Box>
  );
};

const TestOutput: FunctionComponent<TestOutputProps> = ({
  testedTaskExecutionResult,
  onChangeModel,
  status,
  testExecutionId,
}) => {
  const handleJSONChange = (newValue: string) => {
    const parsedObject = tryToJson(newValue);

    if (parsedObject) {
      onChangeModel(parsedObject as Record<string, unknown>);
    }
  };

  return (
    <Box id="test-task-output-container" sx={{ padding: "0 20px" }}>
      <MuiTypography
        sx={{
          padding: "20px 0",
          fontSize: 12,
          fontWeight: 500,
          color: "#858585",
        }}
      >
        Output will appear below when test is complete.
      </MuiTypography>
      <Box
        id="test-task-output"
        sx={{
          padding: "5px 0",
          marginBottom: "10px",
        }}
      >
        <ConductorCodeBlockInput
          label="Output"
          onChange={(val) => handleJSONChange(val)}
          value={JSON.stringify(
            testedTaskExecutionResult?.tasks?.[0]?.outputData as Record<
              string,
              unknown
            >,
            null,
            2,
          )}
          containerStyles={{ borderColor: "#ffffff" }}
          minHeight={150}
          options={{
            lineNumbers: "off",
          }}
        />
      </Box>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <Box id="test-status">
          <WorkflowStatusBadge status={status} />
        </Box>
        <Box sx={{ textAlign: "right" }}>
          <MuiTypography
            sx={{ fontSize: 12, fontWeight: 300, color: "#858585" }}
          >
            Click the link to view the full execution:
          </MuiTypography>
          <MuiTypography
            id="test-execution-id"
            sx={{ fontSize: 12, fontWeight: 300 }}
          >
            <Link href={`/execution/${testExecutionId}`} target="_blank">
              {`${testExecutionId}`}
            </Link>
          </MuiTypography>
        </Box>
      </Box>
    </Box>
  );
};

export const TestTask = ({
  taskModel,
  onChangeModel,
  domain,
  onChangeDomain,
  value,
  maxHeight,
  handleRunTestTask,
  isInProgress,
  onDismiss,
  testExecutionId,
  testedTaskExecutionResult,
  showForm,
}: TestTaskProps) => {
  const status = testedTaskExecutionResult?.status;

  const renderContents = useMemo(() => {
    if (status) {
      return (
        <TestOutput
          testedTaskExecutionResult={testedTaskExecutionResult}
          onChangeModel={onChangeModel}
          status={status}
          testExecutionId={testExecutionId}
        />
      );
    } else if (isInProgress) {
      return <InProgressState />;
    } else {
      return (
        <TestControls
          taskModel={taskModel}
          value={value}
          isInProgress={isInProgress}
          handleRunTestTask={handleRunTestTask}
          onChangeModel={onChangeModel}
          domain={domain}
          onChangeDomain={onChangeDomain}
          showForm={showForm}
        />
      );
    }
  }, [
    status,
    isInProgress,
    domain,
    value,
    taskModel,
    testExecutionId,
    testedTaskExecutionResult,
    handleRunTestTask,
    onChangeDomain,
    onChangeModel,
    showForm,
  ]);

  return (
    <CustomisedTooltip
      id="test-task-modal"
      arrow
      open={true}
      onClose={onDismiss}
      placement="bottom-end"
      disableFocusListener
      disableHoverListener
      disableTouchListener
      slotProps={{
        popper: {
          modifiers: [
            {
              name: "offset",
              options: {
                offset: [0, 8],
              },
            },
          ],
        },
      }}
      title={
        <Box
          sx={{
            width: "100%",
            maxHeight: `${maxHeight}px`,
            overflow: "scroll",
          }}
        >
          <Box sx={{ display: "flex", position: "relative" }}>
            <RocketLaunch />
            <MuiTypography
              sx={{
                fontSize: "14px",
                fontWeight: 500,
                lineHeight: "16px",
                paddingLeft: "5px",
              }}
            >
              Test Task
            </MuiTypography>
            <Box sx={closeIconStyle}>
              <IconButton onClick={onDismiss} sx={closeIconStyle}>
                <XCloseIcon size="20px" />
              </IconButton>
            </Box>
          </Box>
          {renderContents}
        </Box>
      }
    >
      <MuiButton
        size="small"
        variant="text"
        startIcon={<RocketLaunch />}
        color="secondary"
      >
        Test Task
      </MuiButton>
    </CustomisedTooltip>
  );
};

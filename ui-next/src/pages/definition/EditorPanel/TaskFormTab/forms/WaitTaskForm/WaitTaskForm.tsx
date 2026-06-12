import { Box, Link } from "@mui/material";
import _omit from "lodash/omit";
import { FunctionComponent, useMemo } from "react";

import MuiTypography from "components/ui/MuiTypography";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { WaitTaskDef } from "types";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";
import { useGetSetHandler } from "../useGetSetHandler";
import DurationWaitTaskForm from "./DurationWaitTaskForm";
import SelectWaitType from "./SelectWaitType";
import UntilWaitTaskForm from "./UntilWaitTaskForm";
import { detectWaitType } from "./helpers";
import { WaitTaskFormProps, WaitType } from "./types";

const inputParametersPath = "inputParameters";

const updateDuration = (task: WaitTaskDef, val: string) => ({
  ...task,
  inputParameters: {
    ..._omit(task.inputParameters, ["until"]),
    duration: val,
  },
});

const updateUntil = (task: WaitTaskDef, val: string) => ({
  ...task,
  inputParameters: {
    ..._omit(task.inputParameters, ["duration"]),
    until: val,
  },
});

const renderWaitTypeComponent = ({
  waitType,
  task,
  handler,
}: {
  waitType: string;
  task: WaitTaskDef;
  handler: (val: any) => void;
}) => {
  switch (waitType) {
    case "duration":
      return (
        <Box sx={{ px: 6, mb: 6 }}>
          <DurationWaitTaskForm
            value={task.inputParameters?.duration || ""}
            onChange={(val: string) => handler(updateDuration(task, val))}
          />
        </Box>
      );
    case "until":
      return (
        <Box sx={{ px: 6, mb: 6 }}>
          <UntilWaitTaskForm
            value={task.inputParameters?.until || ""}
            onChange={(val: string) => handler(updateUntil(task, val))}
          />
        </Box>
      );
    default:
      return null;
  }
};

export const WaitTaskForm: FunctionComponent<WaitTaskFormProps> = (props) => {
  const { task, onChange } = props;
  const [inputParametersValue, setInputParameters] = useGetSetHandler(
    props,
    inputParametersPath,
  );
  const waitType = useMemo(() => detectWaitType(task), [task]);

  const handleChangeConfiguration = (val: WaitType) => {
    switch (val) {
      case WaitType.DURATION:
        onChange(updateDuration(task, "1 days"));
        break;
      case WaitType.UNTIL:
        onChange(updateUntil(task, ""));
        break;
      default:
        onChange({
          ...task,
          inputParameters: _omit(task.inputParameters, ["duration", "until"]),
        });
        break;
    }
  };

  return (
    <Box width="100%">
      <TaskFormSection title="Wait Type">
        <Box sx={{ minWidth: "100px", mb: 3 }}>
          <SelectWaitType
            options={Object.values(WaitType)}
            value={detectWaitType(task)}
            onChange={handleChangeConfiguration}
          />
        </Box>
        <Box mb={0}>
          {waitType === WaitType.UNTIL && (
            <MuiTypography fontSize={12}>
              Used to&nbsp;
              <Link
                href="https://orkes.io/content/reference-docs/operators/wait#task-parameters"
                target="_blank"
                rel="noreferrer"
                sx={{ fontWeight: 700 }}
              >
                wait until
              </Link>
              &nbsp;a specified date & time, including the&nbsp;
              <MuiTypography component="strong" fontWeight={700} fontSize={12}>
                timezone
              </MuiTypography>
              .
            </MuiTypography>
          )}

          {waitType === WaitType.DURATION && (
            <MuiTypography fontSize={12}>
              Specifies the&nbsp;
              <Link
                href="https://orkes.io/content/reference-docs/operators/wait#task-parameters"
                target="_blank"
                rel="noreferrer"
                sx={{ fontWeight: 700 }}
              >
                wait duration
              </Link>
              &nbsp;in the format: x&nbsp;
              <MuiTypography component="strong" fontWeight={700} fontSize={12}>
                hours
              </MuiTypography>
              &nbsp;x&nbsp;
              <MuiTypography component="strong" fontWeight={700} fontSize={12}>
                days
              </MuiTypography>
              &nbsp;x&nbsp;
              <MuiTypography component="strong" fontWeight={700} fontSize={12}>
                minutes
              </MuiTypography>
              &nbsp;x&nbsp;
              <MuiTypography component="strong" fontWeight={700} fontSize={12}>
                seconds
              </MuiTypography>
            </MuiTypography>
          )}

          {waitType === WaitType.SIGNAL && (
            <>
              <MuiTypography fontSize={12}>
                Used to wait for an external&nbsp;
                <Link
                  href="https://orkes.io/content/reference-docs/operators/wait#task-parameters"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ fontWeight: 700 }}
                >
                  signal.
                </Link>
              </MuiTypography>
            </>
          )}
        </Box>
      </TaskFormSection>
      {renderWaitTypeComponent({
        waitType,
        task,
        handler: onChange,
      })}
      <TaskFormSection title="Input Parameters">
        <ConductorFlatMapFormBase
          autoFocusField={false}
          showFieldTypes={true}
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add parameter"
          hiddenKeys={["until", "duration"]}
          onChange={setInputParameters}
          value={{ ...(inputParametersValue || {}) }}
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

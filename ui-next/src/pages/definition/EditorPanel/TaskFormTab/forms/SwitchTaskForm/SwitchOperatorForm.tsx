import { Box, FormControlLabel, Grid } from "@mui/material";
import { useSelector } from "@xstate/react";
import MuiCheckbox from "components/MuiCheckbox";
import RadioButtonGroup from "components/RadioButtonGroup";
import ConfirmChoiceDialog from "components/ConfirmChoiceDialog";
import ConductorInput from "components/v1/ConductorInput";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import _path from "lodash/fp/path";
import _isEmpty from "lodash/isEmpty";
import _nth from "lodash/nth";
import { useCallback, useContext, useState } from "react";
import { colors } from "theme/tokens/variables";
import { SwitchTaskDef } from "types/TaskType";
import { filterOptionByEvaluatorType } from "utils/deprecatedRadioFilter";
import { updateField } from "utils/fieldHelpers";
import { TaskFormContext } from "../../state";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";
import { useGetSetHandler } from "../useGetSetHandler";
import SwitchCodeBlock from "./SwitchCodeBlock";

const EXPRESSION_PATH = "expression";
const DECISION_CASES_PATH = "decisionCases";

export const SwitchOperatorForm = (props: TaskFormProps) => {
  const { task, onChange } = props;
  const { formTaskActor } = useContext(TaskFormContext);

  const [desicionCases, handleDesicionCases] = useGetSetHandler(
    props,
    DECISION_CASES_PATH,
  );

  const maybeSelectedBranch = useSelector(
    formTaskActor!,
    (state) => state.context.maybeSelectedSwitchBranch,
  );

  const firstInputParameterKey = _nth(
    Object.keys(task?.inputParameters ?? {}),
    0,
  );

  const [showConfirmOverrideDialog, setShowConfirmOverrideDialog] =
    useState(false);

  const radioOptions = filterOptionByEvaluatorType(task?.evaluatorType);
  const DEFAULT_EXPRESSION = `(function () {
    switch ($.${firstInputParameterKey ?? "switchCaseValue"}) {
      case "1":
        return "switch_case";
      case "2":
        return "switch_case_1";
      case "3":
        return "switch_case_2"
    }
  }())`;

  const handleApplySampleScript = useCallback(() => {
    onChange({
      ...task,
      expression: DEFAULT_EXPRESSION,
      ...(_isEmpty(task?.decisionCases)
        ? {
            decisionCases: {
              switch_case: [],
              switch_case_1: [],
              switch_case_2: [],
            },
          }
        : {}),
    });
  }, [task, onChange, DEFAULT_EXPRESSION]);

  const isSingleInputParam =
    Object.keys({ ...task.inputParameters }).length === 1;

  const handleUpdateValueParam = (value: string) => {
    if (isSingleInputParam) {
      const existingParamValue =
        (task.expression && task.inputParameters?.[task.expression]) || "";
      onChange({
        ...task,
        expression: value,
        inputParameters: {
          [value]: existingParamValue,
        },
      });
    } else {
      onChange({ ...task, expression: value });
    }
  };
  const onInputParameterChange = (newValue: Record<string, string>) =>
    onChange(updateField("inputParameters", newValue, task));

  return (
    <Box width="100%">
      <TaskFormSection
        title="Script params"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              autoFocusField={false}
              key={isSingleInputParam ? task?.expression : null}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              onChange={onInputParameterChange}
              value={{ ...(task?.inputParameters || {}) }}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        title=""
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <FormControlLabel
              labelPlacement="start"
              control={
                <RadioButtonGroup
                  items={radioOptions}
                  name={"evaluatorType"}
                  value={_path("evaluatorType", task)}
                  onChange={(_event, value) => {
                    onChange(updateField("evaluatorType", value, task));
                  }}
                />
              }
              label="Evaluate:"
              sx={{
                marginLeft: 0,
                "& .MuiFormControlLabel-label": {
                  fontWeight: 600,
                  color: colors.gray07,
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            {["javascript", "graaljs"].includes(
              task.evaluatorType as string,
            ) ? (
              <>
                <Box
                  display={"flex"}
                  justifyContent={"flex-end"}
                  padding={"2px 0"}
                >
                  <FormControlLabel
                    onChange={() => setShowConfirmOverrideDialog(true)}
                    control={
                      <MuiCheckbox
                        name={"switchScript"}
                        checked={
                          _path(EXPRESSION_PATH, task) === DEFAULT_EXPRESSION
                        }
                      />
                    }
                    label={"Use sample script"}
                    style={{ margin: 0 }}
                  />
                </Box>
                <SwitchCodeBlock
                  label="Code"
                  language="javascript"
                  minHeight={150}
                  autoformat={false}
                  languageLabel="ECMASCRIPT"
                  autoSizeBox={true}
                  task={task as Partial<SwitchTaskDef>}
                  onChange={onChange}
                />
              </>
            ) : (
              <ConductorInput
                fullWidth
                label="Value"
                value={_path(EXPRESSION_PATH, task)}
                onTextInputChange={handleUpdateValueParam}
              />
            )}
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Switch cases"
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              keyColumnLabel="Key"
              hideValue
              addItemLabel="Add more switch cases"
              value={desicionCases}
              onChange={handleDesicionCases}
              isSwitchCase
              focusOnField={maybeSelectedBranch}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
      {showConfirmOverrideDialog && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(confirmed) => {
            if (confirmed) {
              handleApplySampleScript();
            }
            setShowConfirmOverrideDialog(false);
          }}
          message={
            "Applying the sample script will overwrite any existing script. Are you sure you want to proceed?"
          }
        />
      )}
    </Box>
  );
};

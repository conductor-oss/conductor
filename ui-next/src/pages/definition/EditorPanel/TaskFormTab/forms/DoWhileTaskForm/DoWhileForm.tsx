import { Box, FormControlLabel, Grid } from "@mui/material";
import { useSelector } from "@xstate/react";
import MuiCheckbox from "components/ui/MuiCheckbox";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import { ConductorAutoComplete } from "components/ui/inputs";
import ConductorInput from "components/ui/inputs/ConductorInput";
import ConductorInputNumber from "components/ui/inputs/ConductorInputNumber";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import _capitalize from "lodash/capitalize";
import _first from "lodash/first";
import _isNull from "lodash/isNull";
import _omit from "lodash/omit";
import { WorkflowEditContext } from "pages/definition/state";
import { useCallback, useContext, useState } from "react";
import { colors } from "theme/tokens/variables";
import { CommonTaskDef, DoWhileTaskDef } from "types/TaskType";
import { TaskDef, TaskType } from "types/common";
import { getSequentiallySuffix } from "utils";
import { filterOptionByEvaluatorType } from "utils/deprecatedRadioFilter";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";
import { DoWhileCodeBlock } from "./DoWhileCodeBlock";
import { useDoWhileHandler } from "./common";
import { genSampleScripts } from "./sampleScripts";

export const DoWhileForm = ({ task, onChange }: TaskFormProps) => {
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);

  const editorTasks: TaskDef[] = useSelector(
    workflowDefinitionActor!,
    (state) => state.context?.workflowChanges?.tasks,
  );

  const loopOverTasks = useCallback(() => {
    const result: CommonTaskDef[] = [];

    if (editorTasks) {
      editorTasks.forEach((editorTask) => {
        if (
          editorTask.type === TaskType.DO_WHILE &&
          editorTask?.loopOver?.length
        ) {
          result.push(...editorTask.loopOver);
        }
      });
    }

    return result;
  }, [editorTasks]);

  const {
    handleNoLimitChange,
    handleKeepLastNChange,
    handleRadioButtonChange,
    onInputParameterChange,
    onLoopConditionChange,
  } = useDoWhileHandler({
    task,
    onChange,
  });

  const radioOptions = filterOptionByEvaluatorType(task?.evaluatorType);

  const keepLastN = task.inputParameters?.keepLastN;

  const sampleScripts = genSampleScripts(task as DoWhileTaskDef);

  const [selectedScriptOption, setSelectedScriptOption] = useState<
    string | null
  >(null);

  const handleChangeSampleScripts = () => {
    const sampleScriptsValues = Object.values(sampleScripts);
    const selectedValue = sampleScriptsValues.find(
      (value) => value.loopCondition.trim() === selectedScriptOption?.trim(),
    );
    const nonSelectedValue = sampleScriptsValues.find(
      (value) => value.loopCondition.trim() !== selectedScriptOption?.trim(),
    );

    if (selectedValue) {
      // Change loop over task name & task ref name
      const updatedLoopOver =
        selectedValue.loopOver?.map((item) => {
          const { name, taskReferenceName } = getSequentiallySuffix({
            name: item.taskReferenceName,
            refNames: loopOverTasks().map((item) => item.taskReferenceName),
          });

          return {
            ...item,
            name,
            taskReferenceName,
          };
        }) || [];

      const fistLoopOverTask = _first(task?.loopOver);
      const isExampleTaskExisted =
        fistLoopOverTask?.taskReferenceName?.startsWith(
          selectedValue.loopOver?.[0]?.taskReferenceName,
        );

      let updatedLoopOverTasks = [...(task?.loopOver || [])];
      let updatedInputParameters = task?.inputParameters
        ? { ...task?.inputParameters }
        : {};

      const inputKeys = nonSelectedValue
        ? Object.keys(nonSelectedValue?.inputParameters)
        : [];

      // Iterate over array
      // Don't need to add more example task
      if (!isExampleTaskExisted) {
        updatedLoopOverTasks = [...updatedLoopOver, ...updatedLoopOverTasks];
      }

      if (inputKeys) {
        updatedInputParameters = _omit(updatedInputParameters, inputKeys);
      }

      onChange({
        ...task,
        loopCondition: selectedValue?.loopCondition,
        inputParameters: {
          ...selectedValue?.inputParameters,
          ...updatedInputParameters,
        },
        loopOver: updatedLoopOverTasks,
      });
    }
    setSelectedScriptOption(null);
  };

  const handleShowConfirmOverrideDialog = (option: string) => {
    if (option) {
      const maybeSelectedScript = option
        .toLowerCase()
        .replaceAll(" ", "_") as "fixed_number";
      if (maybeSelectedScript != null) {
        const selectedScript = sampleScripts[maybeSelectedScript].loopCondition;
        const isSameScript =
          selectedScript.replaceAll(" ", "") ===
          task?.loopCondition?.replaceAll(" ", "");
        setSelectedScriptOption(isSameScript ? null : selectedScript);
      }
    }
  };

  return (
    <Box width="100%">
      <TaskFormSection
        title="Script Parameters"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              key={task?.loopCondition}
              showFieldTypes
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              onChange={onInputParameterChange}
              value={{ ...(task?.inputParameters || {}) }}
              hiddenKeys={["keepLastN"]}
              autoFocusField={false}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <FormControlLabel
              labelPlacement="start"
              control={
                <RadioButtonGroup
                  items={radioOptions}
                  name={"evaluatorType"}
                  onChange={handleRadioButtonChange}
                  value={task?.evaluatorType}
                />
              }
              label="Loop condition:"
              sx={{
                marginLeft: 0,
                "& .MuiFormControlLabel-label": {
                  fontWeight: 600,
                  color: colors.gray07,
                },
              }}
            />
          </Grid>
          <Grid size={6}>
            {["javascript", "graaljs"].includes(
              task.evaluatorType as string,
            ) && (
              <ConductorAutoComplete
                fullWidth
                label="Sample scripts"
                freeSolo={false}
                options={Object.keys(sampleScripts).map(
                  (key) => _capitalize(`${key.replaceAll("_", " ")}`) as any,
                )}
                onChange={(__, value: any) =>
                  handleShowConfirmOverrideDialog(value)
                }
                data-testid="do-while-sample-scripts-dropdown"
              />
            )}
          </Grid>
          <Grid size={12}>
            {["javascript", "graaljs"].includes(
              task.evaluatorType as string,
            ) ? (
              <DoWhileCodeBlock
                label="Code"
                language="javascript"
                minHeight={150}
                autoformat={false}
                languageLabel="ECMASCRIPT"
                autoSizeBox={true}
                task={task as Partial<DoWhileTaskDef>}
                onChange={onChange}
              />
            ) : (
              <ConductorInput
                value={task?.loopCondition}
                fullWidth
                label="Code"
                onTextInputChange={onLoopConditionChange}
              />
            )}
          </Grid>

          <Grid size={8}>
            <ConductorInputNumber
              label="No. of iterations to keep (if enabled, min value is 2):"
              fullWidth
              value={keepLastN || null}
              onChange={handleKeepLastNChange}
              disabled={!_isNull(keepLastN) && !keepLastN}
              placeholder="min value should be 2"
            />
          </Grid>
          <Grid display="flex" size={4}>
            <FormControlLabel
              labelPlacement="end"
              checked={!_isNull(keepLastN) && !keepLastN}
              control={<MuiCheckbox onChange={handleNoLimitChange} />}
              label="No Limits"
              sx={{
                marginLeft: 6,
                alignSelf: "center",
                "& .MuiFormControlLabel-label": {
                  fontWeight: 600,
                  color: "#767676",
                },
              }}
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
      {selectedScriptOption != null && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(confirmed) => {
            if (confirmed) {
              handleChangeSampleScripts();
            } else {
              setSelectedScriptOption(null);
            }
          }}
          message={
            "Applying the sample script will overwrite any existing script. Are you sure you want to proceed?"
          }
        />
      )}
    </Box>
  );
};

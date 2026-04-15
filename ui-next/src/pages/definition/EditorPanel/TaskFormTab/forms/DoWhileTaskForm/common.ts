import { ChangeEvent } from "react";
import { updateField } from "utils/fieldHelpers";
import { TaskFormProps } from "../types";

export const useDoWhileHandler = ({ task, onChange }: TaskFormProps) => {
  const handleNoLimitChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { checked } = event.target;
    onChange(
      checked
        ? {
            ...task,
            inputParameters: {
              ...task.inputParameters,
              keepLastN: undefined,
            },
          }
        : {
            ...task,
            inputParameters: {
              ...task.inputParameters,
              keepLastN: 2,
            },
          }, //the form machine will remove the attribute when set to undefined
    );
  };

  const handleRadioButtonChange = (
    _evt: ChangeEvent<HTMLInputElement>,
    val: string,
  ) => onChange(updateField("evaluatorType", val, task));

  const handleKeepLastNChange = (val: any) => {
    onChange(updateField("inputParameters.keepLastN", val, task));
  };

  const onInputParameterChange = (newValue: Record<string, string>) =>
    onChange(updateField("inputParameters", newValue, task));

  const onLoopConditionChange = (val: string) =>
    onChange(updateField("loopCondition", val, task));

  const onChangeOptional = (event: ChangeEvent<HTMLInputElement>) =>
    onChange(updateField("optional", event.target.checked, task));

  return {
    handleNoLimitChange,
    handleKeepLastNChange,
    handleRadioButtonChange,
    onInputParameterChange,
    onLoopConditionChange,
    onChangeOptional,
  };
};

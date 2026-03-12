import { useCallback, useState } from "react";
import { FieldValues, UseFormReturn } from "react-hook-form";
import {
  getEditorToFormValue,
  getFormToEditorValue,
} from "utils/reactHookForm";
import { tryToJson } from "utils/utils";

export const useEditorForm = <
  T extends FieldValues,
  TTransformedValues = undefined,
>({
  formMethods,
  hiddenKeys = [],
}: {
  formMethods: UseFormReturn<T, unknown, TTransformedValues>;
  hiddenKeys?: string[];
}): {
  editorValue: string;
  isEditorValid: boolean;
  setInitialFormData: (value: T) => void;
  updateEditorValue: (value?: string) => void;
} => {
  const [editorValue, setEditorValue] = useState<string>("");
  const [isEditorValid, setIsEditorValid] = useState(true);

  const updateEditorValue = useCallback(
    (value?: string) => {
      const editorText =
        value ?? getFormToEditorValue(formMethods.getValues(), hiddenKeys);
      setEditorValue(editorText);

      const parsedValue = tryToJson<T>(editorText);
      if (parsedValue) {
        setIsEditorValid(true);
        if (value) {
          // When user edits in code editor, update form but keep default values
          // This marks the form as dirty (expected behavior)
          formMethods.reset(
            getEditorToFormValue(
              formMethods.getValues() || {},
              parsedValue,
              hiddenKeys,
            ),
            {
              keepDefaultValues: true,
            },
          );
        }
      } else {
        setIsEditorValid(false);
      }
    },
    [formMethods, hiddenKeys],
  );

  const setInitialFormData = useCallback(
    (value: T) => {
      // Normalize the incoming data to match the form's default structure
      // Convert undefined fields to null to prevent isDirty issues
      const currentValues = formMethods.getValues();
      const normalizedValue: T = { ...value };

      Object.keys(currentValues).forEach((key) => {
        if (normalizedValue[key as keyof T] === undefined) {
          (normalizedValue as FieldValues)[key] = null;
        }
      });

      // Reset form with normalized data and explicitly update defaultValues
      // Use keepValues: false and keepDefaultValues: false to ensure
      // the form's internal state is completely reset
      formMethods.reset(normalizedValue, {
        keepValues: false,
        keepDefaultValues: false,
        keepErrors: false,
        keepDirty: false,
        keepIsValid: false,
        keepTouched: false,
        keepIsSubmitted: false,
        keepSubmitCount: false,
      });
      updateEditorValue();
    },
    [formMethods, updateEditorValue],
  );

  return {
    editorValue,
    isEditorValid,
    setInitialFormData,
    updateEditorValue,
  };
};

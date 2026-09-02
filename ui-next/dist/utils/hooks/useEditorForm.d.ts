import { FieldValues, UseFormReturn } from "react-hook-form";
export declare const useEditorForm: <T extends FieldValues, TTransformedValues = undefined>({ formMethods, hiddenKeys, }: {
    formMethods: UseFormReturn<T, unknown, TTransformedValues>;
    hiddenKeys?: string[];
}) => {
    editorValue: string;
    isEditorValid: boolean;
    setInitialFormData: (value: T) => void;
    updateEditorValue: (value?: string) => void;
};

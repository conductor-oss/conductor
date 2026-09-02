import { FieldErrors, FieldValues } from "react-hook-form";
export declare const getEditorToFormValue: (formValues: FieldValues, editorValue: FieldValues, hiddenKeys?: string[]) => any;
export declare const getFormToEditorValue: (formValues: FieldValues, hiddenKeys?: string[]) => string;
export declare const getReactHookFormError: <T extends FieldValues>(errors: FieldErrors<T>) => string | null;
export declare const removeNullAndHiddenKeys: (value: any, hiddenKeys?: string[]) => object;

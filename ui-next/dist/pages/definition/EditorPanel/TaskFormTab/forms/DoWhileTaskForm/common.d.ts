import { ChangeEvent } from "react";
import { TaskFormProps } from "../types";
export declare const useDoWhileHandler: ({ task, onChange }: TaskFormProps) => {
    handleNoLimitChange: (event: ChangeEvent<HTMLInputElement>) => void;
    handleKeepLastNChange: (val: any) => void;
    handleRadioButtonChange: (_evt: ChangeEvent<HTMLInputElement>, val: string) => any;
    onInputParameterChange: (newValue: Record<string, string>) => any;
    onLoopConditionChange: (val: string) => any;
    onChangeOptional: (event: ChangeEvent<HTMLInputElement>) => any;
};

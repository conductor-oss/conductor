import { ActorRef } from "xstate";
import { TaskDefinitionFormMachineEvent } from "./types";
import { ChangeEvent } from "react";
export declare const useTaskDefinitionFormActor: (actor: ActorRef<TaskDefinitionFormMachineEvent>) => readonly [{
    readonly error: any;
    readonly isEditingName: any;
    readonly isEditingDescription: any;
    readonly modifiedTaskDefinition: any;
    readonly originTaskDefinition: any;
}, {
    readonly handleChangeTaskForm: (value: number | string | Record<string, string> | null, event?: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => void;
    readonly handleChangeParameters: ({ name, value, }: {
        name: string;
        value: Record<string, string> | string[];
    }) => void;
    readonly setEditingFieldForm: (name: string) => void;
    readonly handleChangeInputForm: (name: string, value: number | string | Record<string, string | number> | boolean | null | undefined) => void;
}];

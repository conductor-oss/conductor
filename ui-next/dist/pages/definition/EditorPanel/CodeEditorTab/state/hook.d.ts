import { ActorRef } from "xstate";
import { CodeMachineEvents } from "./types";
export declare const useCodeTabActor: (actor: ActorRef<CodeMachineEvents>) => readonly [{
    readonly editorChanges: any;
    readonly referenceText: import("./types").CodeTextReference | undefined;
    readonly shouldTakeToFirstError: boolean;
}, {
    readonly handleEditChanges: (changes: string) => void;
}];

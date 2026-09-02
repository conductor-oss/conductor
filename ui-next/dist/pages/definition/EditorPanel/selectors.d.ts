import { State } from "xstate";
import { DefinitionMachineContext } from "../state";
export declare const versionSelector: (state: State<DefinitionMachineContext>) => string | undefined;
export declare const versionsSelector: (state: State<DefinitionMachineContext>) => number[];
export declare const isSaveRequestSelector: (state: State<DefinitionMachineContext>) => boolean;

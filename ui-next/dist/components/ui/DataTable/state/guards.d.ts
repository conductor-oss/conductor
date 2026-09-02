import { DoneInvokeEvent } from "xstate";
import { DataTableMachineContext, SerializableColumn } from "./types";
export declare const noLocalStorageKey: (context: DataTableMachineContext) => boolean;
export declare const isLocalStorageContentTrusted: ({ columnOrderAndVisibility }: DataTableMachineContext, { data }: DoneInvokeEvent<SerializableColumn[]>) => boolean;

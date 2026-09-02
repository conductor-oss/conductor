import { DataTableMachineContext } from "./types";
export declare const saveOrderAndVisibility: (context: DataTableMachineContext) => Promise<import("./types").SerializableColumn[]>;
export declare const maybePullOrderAndVisibility: (context: DataTableMachineContext) => Promise<{} | null>;

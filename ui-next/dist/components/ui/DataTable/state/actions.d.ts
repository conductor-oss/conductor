import { DataTableMachineContext, SetFilterObjectEvent, SetSearchTermEvent, SetTableDataOrderAndVisibility } from "./types";
export declare const persistOrderAndVisibility: import("xstate").AssignAction<DataTableMachineContext, SetTableDataOrderAndVisibility, SetTableDataOrderAndVisibility>;
export declare const persistSearchTerm: import("xstate").AssignAction<DataTableMachineContext, SetSearchTermEvent, SetSearchTermEvent>;
export declare const persistFilterObj: import("xstate").AssignAction<DataTableMachineContext, SetFilterObjectEvent, SetFilterObjectEvent>;

import { assign } from "xstate";
import {
  DataTableMachineContext,
  SetFilterObjectEvent,
  SetSearchTermEvent,
  SetTableDataOrderAndVisibility,
} from "./types";
// For now we are not managing state for data
/* export const persistData = assign<DataTableMachineContext, SetTableDataEvent>({ */
/*   data: (_context, { data }) => data, */
/* }); */

export const persistOrderAndVisibility = assign<
  DataTableMachineContext,
  SetTableDataOrderAndVisibility
>({
  columnOrderAndVisibility: (_context, { data }) => data,
});

export const persistSearchTerm = assign<
  DataTableMachineContext,
  SetSearchTermEvent
>({
  searchTerm: (context, { searchTerm }) => searchTerm,
});

export const persistFilterObj = assign<
  DataTableMachineContext,
  SetFilterObjectEvent
>({
  filterObj: (context, { filterObj }) => filterObj,
});

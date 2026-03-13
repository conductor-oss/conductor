import { ColumnCustomType } from "components/DataTable/types";
import type { ReactNode } from "react";
export interface SerializableColumn {
  omit: boolean;
  name: string | ReactNode;
  id: string; // We should enforce the use of id,
  wrap?: boolean;
  label: string;
  sortable?: boolean;
  type?: ColumnCustomType;
  searchable?: string | boolean;
  tooltip?: string;
}

export interface FilterObjectItem {
  columnName: string;
  substring: string;
}

export interface DataTableMachineContext {
  columnOrderAndVisibility: SerializableColumn[];
  localStorageKey?: string;
  searchTerm: string;
  filterObj?: FilterObjectItem;
}

export enum DataTableEventTypes {
  SET_DATA = "SET_DATA",
  SET_ORDER_AND_VISIBILITY = "SET_ORDER_AND_VISIBILITY",
  SET_FILTER_OBJ = "SET_FILTER_OBJ",
  SET_SEARCH_TERM = "SET_SEARCH_TERM",
}

export type SetTableDataEvent = {
  type: DataTableEventTypes.SET_DATA;
  data: any[];
};

export type SetTableDataOrderAndVisibility = {
  type: DataTableEventTypes.SET_ORDER_AND_VISIBILITY;
  data: SerializableColumn[];
};

export type SetSearchTermEvent = {
  type: DataTableEventTypes.SET_SEARCH_TERM;
  searchTerm: string;
};

export type SetFilterObjectEvent = {
  type: DataTableEventTypes.SET_FILTER_OBJ;
  filterObj: FilterObjectItem;
};

export type DataTableEvents =
  /* | SetTableDataEvent */
  SetTableDataOrderAndVisibility | SetFilterObjectEvent | SetSearchTermEvent;

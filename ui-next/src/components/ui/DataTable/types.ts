import { ReactNode } from "react";
import type { Selector, TableColumn } from "react-data-table-component";

export type Format<T> = (row: T, rowIndex: number) => ReactNode;

export type PaginationChangePage = (page: number, totalRows: number) => void;

export enum ColumnCustomType {
  DATE = "date",
  JSON = "json",
}

export interface LegacyColumn extends TableColumn<any> {
  id: string;
  name: string | ReactNode;
  label: string;
  type?: ColumnCustomType;
  sortable?: boolean;
  wrap?: boolean;
  searchable?: string | boolean;
  renderer?: (value: any, row: any) => ReactNode;
  searchableFunc?: (row: any) => string;
  tooltip?: string;
}

export interface RenderableColumn extends LegacyColumn {
  format: Format<any>;
  selector: Selector<any>;
  omit: boolean;
}

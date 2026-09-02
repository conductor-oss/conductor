import { TableColumn } from "react-data-table-component";
import { FilterObjectItem } from "./state/types";
import { Format, LegacyColumn, RenderableColumn } from "./types";
type ColumnWithLabel = TableColumn<any> & {
    label?: string;
};
export declare const getColumnLabelById: (columnId: string, columns: ColumnWithLabel[]) => import("react").ReactNode;
export declare const getColumnLabel: (col: ColumnWithLabel) => string;
export declare const getColumnId: (col: TableColumn<any>) => string;
export declare const defaultFilterItemsSorter: (filteredItems: any[]) => any[];
export declare const formatForColumn: (column: LegacyColumn) => Format<any>;
export declare const createDefaultFilterObject: (renderedColumns: RenderableColumn[]) => FilterObjectItem | undefined;
export declare const getNestedValue: <T>(obj: T, path: string) => unknown;
export declare const dynamicSort: <T>({ objA, objB, propertyPath, }: {
    objA: T;
    objB: T;
    propertyPath: string;
}) => number;
export {};

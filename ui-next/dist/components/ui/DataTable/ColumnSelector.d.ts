import { FunctionComponent } from "react";
import { SerializableColumn } from "./state/types";
interface ColumnSorterProps {
    columns: SerializableColumn[];
    defaultShowColumns: string[];
    onColumnVisibilityChange: (columnsOrder: SerializableColumn[], columnsVisibility: SerializableColumn[]) => void;
}
export declare const ColumnsSelector: FunctionComponent<ColumnSorterProps>;
export {};

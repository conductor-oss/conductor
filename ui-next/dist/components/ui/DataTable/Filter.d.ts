import { FunctionComponent } from "react";
import { FilterObjectItem } from "./state";
import { RenderableColumn } from "./types";
export interface FilterProps {
    columns: RenderableColumn[];
    filterObj?: FilterObjectItem;
    setFilterObj: (filterObject: FilterObjectItem) => void;
}
export declare const Filter: FunctionComponent<FilterProps>;

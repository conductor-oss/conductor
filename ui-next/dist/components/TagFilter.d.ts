import { FunctionComponent } from "react";
export interface TagFilterProps {
    data: Record<string, unknown>[];
    onTagFilterChange: (selectedTags: string[]) => void;
    selectedTags: string[];
}
export declare const TagFilter: FunctionComponent<TagFilterProps>;

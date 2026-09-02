import { PopperProps } from "@mui/material";
type OptionsProps = {
    taskName: string;
    taskRef: string;
    type: string;
};
export type AdvancedSearchFieldPopperProps = PopperProps & {
    open?: boolean;
    options: OptionsProps[];
    handleClose: () => void;
    onSelectItem: (value: string | null) => void;
    filteredOptionsCount: number;
    setFilteredOptionsCount: (count: number) => void;
    hoveredItem: string;
    setHoveredItem: (item: string) => void;
    searchTerm: string;
    setSearchTerm: (value: string) => void;
    totalOptionsCount: number;
};
export declare const AdvancedSearchFieldPopper: ({ options, anchorEl, onSelectItem, filteredOptionsCount, setFilteredOptionsCount, hoveredItem, setHoveredItem, searchTerm, setSearchTerm, totalOptionsCount, }: AdvancedSearchFieldPopperProps) => import("react").JSX.Element;
export {};

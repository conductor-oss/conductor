import { GridProps } from "@mui/material";
import { FunctionComponent, ReactNode } from "react";
export interface QuickSearchProps {
    autoFocusValue: boolean;
    createButton?: ReactNode;
    description?: ReactNode;
    onChange: (val: string) => void;
    quickSearchLabel?: ReactNode;
    quickSearchPlaceholder: string;
    searchTerm: string;
    searchModalContainerProps?: GridProps;
}
export declare const QuickSearch: FunctionComponent<QuickSearchProps>;

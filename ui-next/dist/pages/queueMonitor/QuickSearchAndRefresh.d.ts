import { ReactNode } from "react";
export interface QuickSearchProps {
    onChange: (val: string) => void;
    searchTerm: string;
    createButton?: ReactNode;
    description?: ReactNode;
    quickSearchPlaceholder: string;
    label?: ReactNode;
}
export declare const QuickSearchRefresh: ({ label, quickSearchPlaceholder, searchTerm, onChange, }: QuickSearchProps) => import("react").JSX.Element;

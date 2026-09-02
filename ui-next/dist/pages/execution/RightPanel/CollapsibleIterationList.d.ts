import React, { ReactNode } from "react";
export interface CollapsibleIterationListProps<T> {
    items: T[];
    headerLabel: ReactNode;
    selectedLabel?: string;
    renderItem: (item: T, index: number) => ReactNode;
    onSelect: (item: T, index: number) => void;
    isItemSelected?: (item: T, index: number) => boolean;
    trailing?: ReactNode;
    totalItems?: number;
    onPrefetch?: (value: number) => void;
    onJumpTo?: (value: number) => void;
    onScrollEnd?: () => void;
    getItemValue: (item: T) => number;
}
export declare function CollapsibleIterationList<T>({ items, headerLabel, renderItem, onSelect, isItemSelected, trailing, onScrollEnd, getItemValue, }: CollapsibleIterationListProps<T>): React.JSX.Element;

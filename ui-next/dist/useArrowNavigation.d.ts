import { KeyboardEvent } from "react";
type useArrowNavigationProps<T> = {
    onSelect: (item: T) => void;
    options: T[];
    optionsIdGen: (v: T) => string;
    scrollToCenter: boolean;
    hoveredItem: string;
    setHoveredItem: (item: string) => void;
};
export type OptionPropsForItemT = {
    onMouseMove: (e: any) => void;
    onMouseLeave: (e: any) => void;
    id: string;
};
declare function useArrowNavigation<T>({ onSelect, options, optionsIdGen, scrollToCenter, hoveredItem, setHoveredItem, }: useArrowNavigationProps<T>): {
    readonly inputProps: {
        onKeyDown: (event: KeyboardEvent<HTMLElement>) => void;
    };
    readonly optionPropsForItem: (item: T) => OptionPropsForItemT;
    readonly hoveredItem: string;
    readonly moveUp: () => void;
    readonly moveDown: () => void;
};
export default useArrowNavigation;
export type { useArrowNavigationProps };

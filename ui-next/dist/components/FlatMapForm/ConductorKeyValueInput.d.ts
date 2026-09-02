import { ConductorTooltipProps } from "components/ui/ConductorTooltip";
import { FunctionComponent, ReactNode } from "react";
import { FieldType } from "types/index";
interface ConductorKeyValueInputProps {
    onChangeValue: (a: string | number | boolean | null) => void;
    mKey: string;
    onChangeKey: (k: string) => void;
    onDeleteItem: (k: string) => void;
    value: string | Record<string, unknown>;
    hideValue: boolean;
    existingKeys: string[];
    hideButtons: boolean;
    showFieldTypes: boolean;
    focusOnField?: string;
    keyColumnLabel?: string;
    valueColumnLabel?: string;
    typeColumnLabel?: string;
    enableAutocomplete?: boolean;
    autoFocusField?: boolean;
    tooltip?: {
        type?: Omit<ConductorTooltipProps, "children">;
        key?: Omit<ConductorTooltipProps, "children">;
        value?: Omit<ConductorTooltipProps, "children">;
    };
    customInput?: ReactNode;
    placeholder?: string;
}
export type MaybeInputProps = {
    cantCoerce: boolean;
    isContainsError: boolean;
    objValue: string;
    onChangeValue: (value: any) => void;
    onObjChange: (a: string) => void;
    type?: FieldType;
    value: any;
    valueColumnLabel?: string;
    customInput?: ReactNode;
    tooltip?: Omit<ConductorTooltipProps, "children">;
    placeholder?: string;
};
export declare const MaybeInput: (props: MaybeInputProps) => string | number | true | import("react").JSX.Element | Iterable<ReactNode> | null;
export declare const ConductorKeyValueInput: FunctionComponent<ConductorKeyValueInputProps>;
export {};

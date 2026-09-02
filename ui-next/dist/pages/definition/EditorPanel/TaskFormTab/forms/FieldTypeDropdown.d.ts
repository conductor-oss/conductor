import { FunctionComponent, ReactNode } from "react";
import { FieldType } from "types/common";
interface FieldTypeDropdownProps {
    value: any;
    onTypeChange: (value: FieldType) => void;
    hideObjectArray?: boolean;
    allowedTypes?: FieldType[];
    label?: ReactNode;
}
declare const FieldTypeDropdown: FunctionComponent<FieldTypeDropdownProps>;
export default FieldTypeDropdown;

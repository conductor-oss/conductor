import { FunctionComponent } from "react";
import { FieldType } from "types/common";
import { ConductorTooltipProps } from "components/ui/ConductorTooltip";
interface ConductorFieldTypeDropdownProps {
    label?: string;
    type: FieldType;
    onTypeChange: (value: FieldType) => void;
    hideObjectArray?: boolean;
    allowedTypes?: FieldType[];
    tooltip?: Omit<ConductorTooltipProps, "children">;
}
declare const ConductorFieldTypeDropdown: FunctionComponent<ConductorFieldTypeDropdownProps>;
export default ConductorFieldTypeDropdown;

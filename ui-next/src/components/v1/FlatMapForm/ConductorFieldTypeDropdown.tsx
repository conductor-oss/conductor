import { FunctionComponent } from "react";
import {
  FIELD_TYPE_BOOLEAN,
  FIELD_TYPE_NULL,
  FIELD_TYPE_NUMBER,
  FIELD_TYPE_OBJECT,
  FIELD_TYPE_STRING,
  FieldType,
} from "types/common";
import ConductorSelect from "components/v1/ConductorSelect";
import { ConductorTooltipProps } from "components/conductorTooltip/ConductorTooltip";

const fieldTypeOption: FieldType[] = [
  FIELD_TYPE_STRING,
  FIELD_TYPE_NUMBER,
  FIELD_TYPE_BOOLEAN,
  FIELD_TYPE_NULL,
  FIELD_TYPE_OBJECT,
];

function getFieldTypeLabel(type: FieldType): string {
  switch (type) {
    case FIELD_TYPE_NUMBER:
      return "Number";
    case FIELD_TYPE_BOOLEAN:
      return "Boolean";
    case FIELD_TYPE_NULL:
      return "Null";
    case FIELD_TYPE_OBJECT:
      return "Object/Array";
    default:
      return "String";
  }
}
interface ConductorFieldTypeDropdownProps {
  label?: string;
  type: FieldType;
  onTypeChange: (value: FieldType) => void;
  hideObjectArray?: boolean;
  allowedTypes?: FieldType[];
  tooltip?: Omit<ConductorTooltipProps, "children">;
}

const ConductorFieldTypeDropdown: FunctionComponent<
  ConductorFieldTypeDropdownProps
> = ({
  label,
  type,
  onTypeChange,
  hideObjectArray,
  allowedTypes = fieldTypeOption,
  tooltip,
}) => {
  const filteredOptions = fieldTypeOption.reduce<
    {
      label: string;
      value: string;
    }[]
  >((result, type) => {
    if (
      allowedTypes.includes(type) &&
      (!hideObjectArray || type !== FIELD_TYPE_OBJECT)
    ) {
      return [...result, { label: getFieldTypeLabel(type), value: type }];
    }

    return result;
  }, []);

  return (
    <ConductorSelect
      fullWidth
      label={label}
      value={type}
      onChange={(ev) => {
        onTypeChange(ev.target.value as FieldType);
      }}
      tooltip={tooltip}
      items={filteredOptions}
    />
  );
};

export default ConductorFieldTypeDropdown;

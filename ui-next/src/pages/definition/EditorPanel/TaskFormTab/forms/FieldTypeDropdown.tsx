import { FunctionComponent, ReactNode } from "react";
import { MenuItem } from "@mui/material";
import {
  FIELD_TYPE_BOOLEAN,
  FIELD_TYPE_NULL,
  FIELD_TYPE_NUMBER,
  FIELD_TYPE_OBJECT,
  FIELD_TYPE_STRING,
  FieldType,
} from "types/common";
import { inferType } from "utils";
import ConductorSelect from "components/v1/ConductorSelect";

const fieldTypeOption: FieldType[] = [
  FIELD_TYPE_STRING,
  FIELD_TYPE_NUMBER,
  FIELD_TYPE_BOOLEAN,
  FIELD_TYPE_NULL,
  FIELD_TYPE_OBJECT,
];

function getFieldTypeLabel(type: string): string {
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
interface FieldTypeDropdownProps {
  value: any;
  onTypeChange: (value: FieldType) => void;
  hideObjectArray?: boolean;
  allowedTypes?: FieldType[];
  label?: ReactNode;
}

const FieldTypeDropdown: FunctionComponent<FieldTypeDropdownProps> = ({
  value,
  onTypeChange,
  hideObjectArray,
  allowedTypes = fieldTypeOption,
  label,
}) => {
  const filteredOptions = fieldTypeOption.filter(
    (type) =>
      allowedTypes.includes(type) &&
      (!hideObjectArray || type !== FIELD_TYPE_OBJECT),
  );

  return (
    <ConductorSelect
      label={label}
      fullWidth
      value={inferType(value)}
      onChange={(ev) => {
        onTypeChange(ev.target.value as FieldType);
      }}
    >
      {filteredOptions.map((type) => (
        <MenuItem key={type} value={type}>
          {getFieldTypeLabel(type)}
        </MenuItem>
      ))}
    </ConductorSelect>
  );
};

export default FieldTypeDropdown;

import { Grid } from "@mui/material";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { FunctionComponent } from "react";
import { FIELD_TYPE_OBJECT, FIELD_TYPE_STRING, FieldType } from "types/common";
import {
  DEFAULT_FIELD_VALUES_CONF,
  ValueInputDefaultValues,
  castToType,
  inferType,
  useCoerceToObject,
} from "utils";
import FieldTypeDropdown from "./FieldTypeDropdown";

interface DynamicInputProps {
  isContainsError: boolean;
  onChangeValue: (value: any) => void;
  type: FieldType;
  value: any;
  valueColumnLabel?: string;
  dropDownOptions?: string[];
}

const DynamicInput = ({
  isContainsError,
  onChangeValue,
  type,
  value,
  valueColumnLabel = "",
}: DynamicInputProps) => {
  const [onObjChange, objValue, cantCoerce] = useCoerceToObject(
    onChangeValue,
    value,
  );
  switch (type) {
    case FIELD_TYPE_OBJECT:
      return (
        <ConductorCodeBlockInput
          label={valueColumnLabel}
          onChange={onObjChange}
          value={objValue}
          error={cantCoerce}
          containerProps={{ sx: { width: "100%" } }}
        />
      );

    default:
      return (
        <ConductorAutocompleteVariables
          value={value}
          label={valueColumnLabel}
          onChange={(val) => onChangeValue(castToType(val, inferType(value)))}
          helperText={isContainsError ? " " : ""}
        />
      );
  }
};

interface ValueInputProps {
  onChangeValue: (a: string) => void;
  value: string | object;
  valueLabel?: string;
  defaultObjectValue?: ValueInputDefaultValues;
  dropDownOptions?: string[];
}

export const ConductorObjectOrStringInput: FunctionComponent<
  ValueInputProps
> = ({
  onChangeValue,
  value,
  valueLabel = "Value",
  defaultObjectValue = DEFAULT_FIELD_VALUES_CONF,
  dropDownOptions = [],
}) => {
  const currentType = inferType(value);
  const typeLabel = valueLabel ? `${valueLabel} type` : "Type";

  return (
    <Grid container spacing={2}>
      <Grid flexGrow={1} sx={{ minWidth: "150px", width: "auto" }}>
        <DynamicInput
          isContainsError={false}
          onChangeValue={onChangeValue}
          type={currentType}
          value={value}
          valueColumnLabel={valueLabel}
          dropDownOptions={dropDownOptions}
        />
      </Grid>

      <Grid sx={{ minWidth: "150px" }}>
        <FieldTypeDropdown
          label={typeLabel}
          value={value}
          onTypeChange={(type: FieldType) => {
            onChangeValue(castToType(value, type, defaultObjectValue));
          }}
          allowedTypes={[FIELD_TYPE_STRING, FIELD_TYPE_OBJECT]}
        />
      </Grid>
    </Grid>
  );
};

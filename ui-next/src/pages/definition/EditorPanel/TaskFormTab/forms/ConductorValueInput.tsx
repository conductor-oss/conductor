import { Grid, SxProps } from "@mui/material";
import _isEmpty from "lodash/isEmpty";
import { FunctionComponent } from "react";

import { ConductorAutoComplete } from "components/v1";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { FIELD_TYPE_OBJECT, FIELD_TYPE_STRING, FieldType } from "types/common";
import {
  DEFAULT_FIELD_VALUES_CONF,
  ValueInputDefaultValues,
  castToType,
  inferType,
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
  dropDownOptions = [],
}: DynamicInputProps) => {
  switch (type) {
    case FIELD_TYPE_OBJECT:
      return (
        <ConductorAutoComplete
          fullWidth
          label={valueColumnLabel}
          options={dropDownOptions}
          multiple
          freeSolo={_isEmpty(dropDownOptions)}
          onChange={(__, val: string[]) => {
            onChangeValue(val);
          }}
          value={value}
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
  value: string | boolean;
  valueLabel?: string;
  defaultObjectValue?: ValueInputDefaultValues;
  dropDownOptions?: string[];
  keyStyle?: SxProps;
  valueStyle?: SxProps;
}

export const ConductorValueInput: FunctionComponent<ValueInputProps> = ({
  onChangeValue,
  value,
  valueLabel = "Value",
  defaultObjectValue = DEFAULT_FIELD_VALUES_CONF,
  dropDownOptions = [],
  keyStyle = {},
  valueStyle = {},
}) => {
  const currentType = inferType(value);
  const typeLabel = valueLabel ? `${valueLabel} type` : "Type";

  return (
    <Grid container sx={{ width: "100%" }} spacing={2}>
      <Grid flexGrow={1} sx={{ minWidth: "150px", width: "auto", ...keyStyle }}>
        <DynamicInput
          isContainsError={false}
          onChangeValue={onChangeValue}
          type={currentType}
          value={value}
          valueColumnLabel={valueLabel}
          dropDownOptions={dropDownOptions}
        />
      </Grid>
      <Grid sx={{ minWidth: "150px", ...valueStyle }}>
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

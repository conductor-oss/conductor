import { Box, Grid, MenuItem } from "@mui/material";
import Button from "components/MuiButton";
import IconButton from "components/MuiIconButton";
import _isEmpty from "lodash/isEmpty";
import _isNull from "lodash/isNull";
import FieldTypeDropdown from "pages/definition/EditorPanel/TaskFormTab/forms/FieldTypeDropdown";
import maybeVariable from "pages/definition/EditorPanel/TaskFormTab/forms/maybeVariableHOC";
import { FunctionComponent, ReactNode } from "react";
import { FieldType } from "types/common";
import { adjust, remove } from "utils/array";
import { ButtonPosition } from "utils/constants/common";
import { castToType } from "utils/helpers";
import { ConductorEmptyGroupField } from "./ConductorEmptyGroupField";
import ConductorInput from "./ConductorInput";
import ConductorSelect from "./ConductorSelect";
import { ConductorAutocompleteVariables } from "./FlatMapForm/ConductorAutocompleteVariables";
import AddIcon from "./icons/AddIcon";
import TrashIcon from "./icons/TrashIcon";

interface RemovableFieldProps {
  onChange: (value: any) => void;
  value?: string;
  onRemove?: () => void;
  isError?: boolean;
  hasAtLeastOne?: boolean;
  placeholder?: string;
  customInput?: ReactNode;
  inputLabel?: ReactNode;
  showType?: boolean;
  addButtonPosition?: ButtonPosition;
  helperText?: ReactNode;
  typeLabel?: ReactNode;
}

const MaybeInput = ({
  customInput,
  inputLabel,
  value,
  placeholder,
  isError,
  helperText,
  onChange,
}: RemovableFieldProps) => {
  const trimmedValue = typeof value === "string" ? value.trim() : value;
  const isEmptyValue = _isEmpty(trimmedValue);
  const isNullValue = _isNull(value);

  return customInput ? (
    customInput
  ) : (
    <ConductorInput
      fullWidth
      label={inputLabel}
      value={isNullValue ? "null" : value}
      placeholder={placeholder}
      onTextInputChange={(val) => onChange(val)}
      disabled={isNullValue}
      error={isError && isEmptyValue}
      helperText={isEmptyValue && helperText}
    />
  );
};

const RemovableField: FunctionComponent<RemovableFieldProps> = (props) => {
  const { onChange, value, onRemove, showType, typeLabel, inputLabel } = props;

  return (
    <Grid
      container
      sx={{ width: "100%", display: "grid", gridTemplateColumns: "1fr auto" }}
      spacing={4}
      size={12}
    >
      <Grid container sx={{ width: "100%" }} spacing={4}>
        {showType && (
          <Grid size={3}>
            <FieldTypeDropdown
              label={typeLabel}
              value={value}
              onTypeChange={(type: FieldType) =>
                onChange(castToType(value, type))
              }
              hideObjectArray
            />
          </Grid>
        )}

        <Grid flexGrow={1}>
          {typeof value === "boolean" ? (
            <ConductorSelect
              label={inputLabel}
              fullWidth
              value={`${value}`}
              size="small"
              onChange={(ev) => {
                onChange(ev.target.value === "true");
              }}
            >
              <MenuItem value={"true"}>True</MenuItem>
              <MenuItem value={"false"}>False</MenuItem>
            </ConductorSelect>
          ) : value === null ? (
            <></>
          ) : (
            <MaybeInput {...props} />
          )}
        </Grid>
      </Grid>
      <Grid>
        {onRemove && (
          <IconButton
            onClick={() => onRemove!()}
            style={{ paddingTop: "0.42em" }}
          >
            <TrashIcon />
          </IconButton>
        )}
      </Grid>
    </Grid>
  );
};

export interface ConductorArrayFieldProps {
  value: string[];
  onChange: (val: string[]) => void;
  isError?: boolean;
  placeholder?: string;
  customInput?: ReactNode;
  addButtonLabel?: string;
  inputLabel?: ReactNode;
  showType?: boolean;
  addButtonPosition?: ButtonPosition;
  disabledAddButton?: boolean;
  enableAutocomplete?: boolean;
  typeLabel?: ReactNode;
  helperText?: ReactNode;
}

const ConductorArrayFieldBase: FunctionComponent<ConductorArrayFieldProps> = ({
  value = [],
  onChange,
  isError,
  placeholder,
  customInput,
  addButtonLabel = "Add",
  inputLabel = "Value",
  typeLabel = "Type",
  showType,
  addButtonPosition,
  disabledAddButton,
  enableAutocomplete,
  helperText,
}) => {
  const handleValueChange = (index: number) => (newValue: string) => {
    onChange(adjust(index, () => newValue, value));
  };

  const handleRemoveValue = (index: number) => () => {
    onChange(remove(index, 1, value));
  };

  const handleAddItem = () => onChange(value.concat(""));

  return value.length === 0 ? (
    <ConductorEmptyGroupField
      addButtonLabel={addButtonLabel}
      handleAddItem={handleAddItem}
    />
  ) : (
    <>
      <Box
        sx={{
          p: 6,
          borderRadius: "6px",
          border: "1px solid rgba(0, 0, 0, 0.12)",
          background: "rgba(0, 0, 0, 0.04)",
        }}
      >
        <Grid container sx={{ width: "100%" }} spacing={4}>
          {value.map((val, index) => (
            <RemovableField
              key={`removable-field-${index}`}
              value={val}
              onChange={handleValueChange(index)}
              onRemove={handleRemoveValue(index)}
              isError={isError}
              placeholder={placeholder}
              customInput={
                enableAutocomplete ? (
                  <ConductorAutocompleteVariables
                    label={inputLabel}
                    value={val}
                    fullWidth
                    onChange={handleValueChange(index)}
                  />
                ) : (
                  customInput
                )
              }
              inputLabel={inputLabel}
              typeLabel={typeLabel}
              showType={showType}
              addButtonPosition={addButtonPosition}
              helperText={helperText}
            />
          ))}
        </Grid>
      </Box>
      <Button
        size={addButtonPosition === ButtonPosition.RIGHT ? "medium" : "small"}
        onClick={handleAddItem}
        startIcon={<AddIcon />}
        disabled={disabledAddButton}
        sx={{ mt: 3 }}
      >
        {addButtonLabel}
      </Button>
    </>
  );
};

const ConductorArrayField = maybeVariable(ConductorArrayFieldBase);
export { ConductorArrayField, ConductorArrayFieldBase };

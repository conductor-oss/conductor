import { Grid } from "@mui/material";
import Button from "components/ui/buttons/MuiButton";
import IconButton from "components/ui/buttons/MuiIconButton";
import AddIcon from "components/icons/AddIcon";
import TrashIcon from "components/icons/TrashIcon";
import _isEmpty from "lodash/isEmpty";
import maybeVariable from "pages/definition/EditorPanel/TaskFormTab/forms/maybeVariableHOC";
import { FunctionComponent, ReactNode } from "react";
import { adjust, remove } from "utils";
import { ButtonPosition } from "utils/constants/common";
import { ConductorAutocompleteVariables } from "./ConductorAutocompleteVariables";

interface RemovableFieldProps {
  onChange: (value: any) => void;
  value?: any;
  onRemove?: () => void;
  addButtonPosition?: ButtonPosition;
  isError?: boolean;
  hasAtLeastOne?: boolean;
  placeholder?: string;
  label?: ReactNode;
}

const getWidth = (currentWidth: number, addButtonPosition?: ButtonPosition) => {
  if (addButtonPosition === ButtonPosition.RIGHT) {
    return currentWidth - 2;
  }

  return currentWidth;
};

const RemovableField: FunctionComponent<RemovableFieldProps> = ({
  onChange,
  value,
  onRemove,
  addButtonPosition,
  isError,
  hasAtLeastOne,
  placeholder,
  label,
}) => {
  const isEmptyValue = _isEmpty(value?.trim());

  return (
    <>
      <Grid
        size={{
          xs: hasAtLeastOne ? 11 : 10,
          sm: hasAtLeastOne ? 12 : 11,

          md: hasAtLeastOne
            ? getWidth(12, addButtonPosition)
            : getWidth(11, addButtonPosition),
        }}
      >
        <ConductorAutocompleteVariables
          fullWidth
          label={label}
          value={value ?? ""}
          placeholder={placeholder ?? ""}
          onChange={(val: any) => onChange(val)}
          error={isError && isEmptyValue}
        />
      </Grid>
      {!hasAtLeastOne && (
        <Grid alignSelf="center" size={1}>
          {onRemove && (
            <IconButton onClick={() => onRemove!()}>
              <TrashIcon />
            </IconButton>
          )}
        </Grid>
      )}
    </>
  );
};

interface ConductorAutocompleteArrayFieldProps {
  value: any[];
  onChange: (val: any[]) => void;
  addButtonPosition?: ButtonPosition;
  isError?: boolean;
  hasAtLeastOne?: boolean;
  placeholder?: string;
  label?: ReactNode;
}

const ConductorAutocompleteArrayFieldBase: FunctionComponent<
  ConductorAutocompleteArrayFieldProps
> = ({
  value = [],
  onChange,
  addButtonPosition,
  isError,
  hasAtLeastOne,
  placeholder,
  label = "Value",
}) => {
  const handleValueChange = (index: number) => (newValue: string) => {
    onChange(adjust(index, () => newValue, value));
  };
  const handleRemoveValue = (index: number) => () => {
    onChange(remove(index, 1, value));
  };
  const handleAddItem = () => onChange(value.concat(""));

  return (
    <Grid container sx={{ width: "100%" }} spacing={2}>
      {value.map((val, index) => (
        <RemovableField
          key={index}
          label={label}
          value={val}
          onChange={handleValueChange(index)}
          onRemove={handleRemoveValue(index)}
          addButtonPosition={addButtonPosition}
          isError={isError}
          hasAtLeastOne={value.length === 1 && hasAtLeastOne}
          placeholder={placeholder}
        />
      ))}
      <Grid
        size={{
          sm: 12,
          md: addButtonPosition === ButtonPosition.RIGHT ? 2 : 12,
        }}
      >
        <Button
          size={addButtonPosition === ButtonPosition.RIGHT ? "medium" : "small"}
          onClick={handleAddItem}
          startIcon={<AddIcon />}
        >
          Add
        </Button>
      </Grid>
    </Grid>
  );
};

const AutocompleteArrayField = maybeVariable(
  ConductorAutocompleteArrayFieldBase,
);
export { AutocompleteArrayField };

import { Autocomplete, ListItem, ListItemText, Popper } from "@mui/material";
import {
  AppWindow as ApplicationIcon,
  UsersThree as GroupIcon,
  User as UserIcon,
} from "@phosphor-icons/react";
import TagChip from "components/ui/TagChip";
import ConductorInput from "components/ui/inputs/ConductorInput";
import XCloseIcon from "components/icons/XCloseIcon";
import { CSSProperties, FunctionComponent } from "react";
import { autocompleteStyle } from "shared/styles";
import { SelectableOption } from "./types";

interface SubjectMultiPickerProps {
  multiple: boolean;
  options: SelectableOption[];
  onChange: (val: SelectableOption | SelectableOption[]) => void;
  label: string;
  value?: any;
  required?: boolean;
  growPopper?: boolean;
}

const ICON_SIZE = 16;

export const SubjectMultiPicker: FunctionComponent<SubjectMultiPickerProps> = ({
  multiple,
  options,
  onChange,
  label,
  value,
  required = false,
  growPopper,
}) => {
  const popperStyle = (style: CSSProperties | undefined) => {
    return growPopper ? { maxWidth: "300px" } : style;
  };
  return (
    <Autocomplete
      PopperComponent={(props) => (
        <Popper {...props} style={popperStyle(props.style)} />
      )}
      value={value}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      multiple={multiple}
      options={options as SelectableOption[]}
      getOptionLabel={(option: any) => option?.display || ""}
      freeSolo
      renderTags={(value, getTagProps) =>
        value.map((option: SelectableOption, index) => {
          const { key, ...otherTagProps } = getTagProps({ index });
          return (
            <TagChip
              key={key}
              icon={
                option.type === "user" ? (
                  <UserIcon size={ICON_SIZE} />
                ) : option.type === "group" ? (
                  <GroupIcon size={ICON_SIZE} />
                ) : (
                  <ApplicationIcon size={ICON_SIZE} />
                )
              }
              label={option.display}
              {...otherTagProps}
            />
          );
        })
      }
      filterSelectedOptions
      onChange={(_event, newValue: any) => {
        if (newValue !== value) {
          onChange(newValue as SelectableOption | SelectableOption[]);
        }
      }}
      onInputChange={(_event, newInputValue, reason) => {
        // Only handle user input, not programmatic changes
        if (reason === "input") {
          const newOption = {
            value: newInputValue,
            id: newInputValue,
            display: newInputValue,
          } as SelectableOption;
          if (newOption.value !== value?.value) {
            onChange(newOption);
          }
        }
      }}
      renderOption={(props, option) => (
        <ListItem disablePadding {...props}>
          <ListItemText primary={option.display} />
        </ListItem>
      )}
      renderInput={(params) => (
        <ConductorInput
          {...params}
          label={label}
          required={required}
          placeholder="Select subject"
        />
      )}
      sx={[autocompleteStyle({ value })]}
      clearIcon={<XCloseIcon />}
    />
  );
};

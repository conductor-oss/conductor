import { Popper } from "@mui/material";
import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import { CSSProperties, FunctionComponent, ReactNode } from "react";
import { autocompleteStyle } from "shared/styles";
import { fontWeights } from "theme/tokens/variables";
import ConductorInput from "./ConductorInput";
import XCloseIcon from "./icons/XCloseIcon";

const filter = createFilterOptions();

interface ConductorAutoCompleteWithDescriptionProps {
  value?: string;
  options?: { name: string; description: ReactNode }[];
  error?: boolean;
  helperText?: string;
  onChange: (value: string) => void;
  placeholder?: string;
  growPopper?: boolean;
  label?: ReactNode;
  disableClearable?: boolean;
}

export const ConductorAutoCompleteWithDescription: FunctionComponent<
  ConductorAutoCompleteWithDescriptionProps
> = ({
  value,
  options = [],
  error = false,
  helperText,
  onChange,
  placeholder = "",
  growPopper,
  label,
  disableClearable = false,
}) => {
  const popperStyle = (style: CSSProperties | undefined) => {
    return growPopper ? { maxWidth: "300px" } : style;
  };
  return (
    <Autocomplete
      PopperComponent={(props) => (
        <Popper {...props} style={popperStyle(props.style)} />
      )}
      value={value ? value : ""}
      isOptionEqualToValue={(option: any, currentValue: any) =>
        option?.name === currentValue
      }
      autoHighlight
      componentsProps={{ paper: { elevation: 3 } }}
      onChange={(_event, newValue: any) => {
        onChange(newValue?.name);
      }}
      filterOptions={(options, params) => {
        const filtered = filter(options, params);
        return filtered;
      }}
      id="assignment-type-dialog"
      options={options}
      getOptionLabel={(option) => {
        // e.g value selected with enter, right from the input
        if (typeof option === "string") {
          return option;
        }
        return option?.name;
      }}
      selectOnFocus
      clearOnBlur
      handleHomeEndKeys
      renderOption={(props, option) => {
        const { key, ...otherProps } = props;
        return (
          <li
            key={key}
            {...otherProps}
            style={{
              ...otherProps.style,
              borderBottom: "1px solid",
              borderColor: "rgba(128, 128, 128, .25)",
            }}
          >
            <Box
              sx={{
                paddingTop: 2,
                paddingBottom: 2,
              }}
            >
              <Box
                sx={{
                  fontWeight: fontWeights.fontWeight1,
                }}
              >
                {option.name}
              </Box>
              <Box>{option.description}</Box>
            </Box>
          </li>
        );
      }}
      freeSolo
      renderInput={(params) => (
        <ConductorInput
          {...params}
          label={label}
          error={error}
          helperText={helperText}
          placeholder={placeholder}
          size="small"
        />
      )}
      sx={[autocompleteStyle({ value })]}
      clearIcon={<XCloseIcon />}
      disableClearable={disableClearable}
    />
  );
};

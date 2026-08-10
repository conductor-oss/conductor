import { CSSProperties, FunctionComponent, ReactNode } from "react";
import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import { fontWeights } from "theme/tokens/variables";
import TextField from "@mui/material/TextField";
import { Popper } from "@mui/material";

const filter = createFilterOptions();

interface AutoCompleteWithDescriptionProps {
  value?: string;
  options?: { name: string; description: ReactNode }[];
  error?: boolean;
  helperText?: string;
  onChange: (value: string) => void;
  placeholder?: string;
  growPopper?: boolean;
}

export const AutoCompleteWithDescription: FunctionComponent<
  AutoCompleteWithDescriptionProps
> = ({
  value,
  options = [],
  error = false,
  helperText,
  onChange,
  placeholder = "",
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
      renderOption={(props, option) => (
        <li
          {...props}
          style={{
            ...props.style,
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
      )}
      freeSolo
      renderInput={(params) => (
        <TextField
          {...params}
          error={error}
          helperText={helperText}
          placeholder={placeholder}
          size="small"
        />
      )}
    />
  );
};

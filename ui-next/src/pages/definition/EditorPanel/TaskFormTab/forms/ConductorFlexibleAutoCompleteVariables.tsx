import { Box, Grid } from "@mui/material";
import match from "autosuggest-highlight/match";
import parse from "autosuggest-highlight/parse";
import { SyntheticEvent } from "react";

import { ConductorAutoComplete } from "components/v1";

const ConductorFlexibleAutoCompleteVariables = ({
  options = [],
  value = "",
  onChange = (_newValues) => {},
  label = "",
}: {
  options: Array<string>;
  value?: string;
  onChange?: (newValues: string) => void;
  label?: string;
}) => {
  const handleChange = (e: any, data: string) => {
    onChange(data);
  };

  const handleSelect = (__: SyntheticEvent<Element, Event>, data: any) => {
    let result = "";
    if (data) {
      result = value + data.toString();
    }
    onChange(result);
  };

  return (
    <Box>
      <Grid container sx={{ width: "100%" }}>
        <Grid size={12}>
          <ConductorAutoComplete
            label={label}
            freeSolo
            fullWidth
            id="autocomplete-chache-key-selector"
            options={options}
            disableClearable
            getOptionLabel={(option) => option}
            renderOption={(props, option, { inputValue }) => {
              const matches = match(option as string, inputValue);
              const parts = parse(option as string, matches);
              return (
                <li {...props}>
                  {parts.map((part, index) => (
                    <span
                      key={index}
                      style={{
                        fontWeight: part.highlight ? 700 : 400,
                      }}
                    >
                      {part.text}
                    </span>
                  ))}
                </li>
              );
            }}
            // renderInput={(params) => <Input label={label} {...params} />}
            value={value}
            onChange={handleSelect}
            onInputChange={handleChange}
          />
        </Grid>
      </Grid>
    </Box>
  );
};

export default ConductorFlexibleAutoCompleteVariables;

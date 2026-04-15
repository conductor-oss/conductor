import { Box } from "@mui/material";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { ChangeEvent } from "react";
import { colors } from "theme/tokens/variables";

export interface RadioButtonGroupProp {
  ariaLabel?: string;
  items: {
    disabled?: boolean;
    value: string | number;
    label: string;
    helperText?: string;
  }[];
  name: string;
  onChange?: (evt: ChangeEvent<HTMLInputElement>, val: string) => void;
  value?: string | number;
}

const RadioButtonGroup = ({
  ariaLabel,
  items,
  name,
  onChange,
  value,
}: RadioButtonGroupProp) => {
  return (
    <RadioGroup
      row
      aria-labelledby={ariaLabel}
      name={name}
      value={value}
      onChange={onChange}
      sx={{ marginLeft: 3 }}
    >
      {items.map((item, index) => (
        <FormControlLabel
          key={index}
          value={item.value ?? index}
          control={<Radio />}
          id={`${item.label}-radio-btn`}
          label={
            <Box>
              <>{item.label}</>
              {item.helperText && (
                <Box
                  sx={{
                    fontSize: "10px",
                    fontWeight: 300,
                    color: colors.gray07,
                  }}
                >
                  {item.helperText}
                </Box>
              )}
            </Box>
          }
          disabled={item.disabled}
        />
      ))}
    </RadioGroup>
  );
};

export default RadioButtonGroup;

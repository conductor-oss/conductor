import { FunctionComponent } from "react";
import { WaitType } from "pages/definition/EditorPanel/TaskFormTab/forms/WaitTaskForm/types";
import { FormControl } from "@mui/material";
import { colors } from "theme/tokens/variables";
import _capitalize from "lodash/capitalize";
import Button from "components/ui/buttons/MuiButton";
import ButtonGroup from "components/ui/buttons/MuiButtonGroup";

const SelectWaitType: FunctionComponent<{
  options: WaitType[];
  onChange: (val: WaitType) => void;
  value: string;
}> = ({ options, onChange, value }) => {
  return (
    <FormControl>
      <ButtonGroup variant="outlined" color="inherit">
        {options.map((option) => (
          <Button
            id={`wait-type-${option}`}
            key={option}
            onClick={() => onChange(option)}
            sx={[
              value === option
                ? {
                    "&.MuiButtonGroup-grouped": {
                      backgroundColor: colors.blueLightMode,
                      color: colors.white,
                      border: "2px",

                      ":hover": {
                        backgroundColor: colors.blueBackground,
                      },

                      ":not(:first-of-type)": {
                        borderRadius: "6px",
                        ml: "-4px",
                      },

                      ":not(:last-of-type)": {
                        borderRadius: "6px",
                        mr: "-4px",
                      },

                      "+.MuiButtonGroup-grouped": {
                        borderLeft: 0,
                        ml: 0,
                      },
                    },
                  }
                : {
                    borderColor: colors.gray15,
                    color: colors.gray15,
                    borderRadius: "6px",

                    "&.MuiButtonGroup-grouped": {
                      ":hover": {
                        color: colors.black,
                      },
                    },
                  },
            ]}
          >
            {_capitalize(option)}
          </Button>
        ))}
      </ButtonGroup>
    </FormControl>
  );
};

export default SelectWaitType;

import { FormControl, InputLabel } from "@mui/material";
import Button from "./MuiButton";
import MuiButtonGroup from "./MuiButtonGroup";

const ButtonGroup = ({ options, label, style, classes, ...props }) => {
  return (
    <FormControl style={style} classes={classes}>
      {label && <InputLabel>{label}</InputLabel>}
      <MuiButtonGroup color="secondary" variant="outlined" {...props}>
        {options.map((option, idx) => (
          <Button key={idx} onClick={option.onClick}>
            {option.label}
          </Button>
        ))}
      </MuiButtonGroup>
    </FormControl>
  );
};

export default ButtonGroup;

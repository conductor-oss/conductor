import React from "react";
import {
  FormControl,
  InputLabel,
  ButtonGroup,
  Button,
} from "@material-ui/core";

export default function ({ options, label, style, classes, ...props }) {
  return (
    <FormControl style={style} classes={classes}>
      {label && <InputLabel>{label}</InputLabel>}
      <ButtonGroup color="secondary" variant="outlined" {...props}>
        {options.map((option, idx) => (
          <Button key={idx} onClick={option.onClick}>
            {option.label}
          </Button>
        ))}
      </ButtonGroup>
    </FormControl>
  );
}

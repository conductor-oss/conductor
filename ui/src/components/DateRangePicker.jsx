import React from "react";
import { Input } from "./";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  wrapper: {
    display: "flex",
  },
  input: {
    marginRight: 5,
    flex: "0 1 50%",
  },
  quick: {
    flex: "0 0 auto",
  },
});

export default function DateRangePicker({
  onFromChange,
  from,
  onToChange,
  to,
  label,
  disabled,
}) {
  const classes = useStyles();

  return (
    <div className={classes.wrapper}>
      <Input
        className={classes.input}
        label={label && `${label} - From`}
        value={from}
        onChange={onFromChange}
        type="datetime-local"
        fullWidth
        clearable
        disabled={disabled}
      />
      <Input
        className={classes.input}
        label={label && `${label} - To`}
        value={to}
        onChange={onToChange}
        type="datetime-local"
        fullWidth
        clearable
        disabled={disabled}
      />
    </div>
  );
}

import React from "react";
import { FormControl, InputLabel } from "@material-ui/core";
import { Input, ButtonGroup } from "./";
import { makeStyles } from "@material-ui/styles";
import { subHours, format } from "date-fns";

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
}) {
  const classes = useStyles();
  const quickRange = (hours) => {
    const to = new Date();
    const from = subHours(to, hours);

    onFromChange(format(from, "yyyy-MM-dd'T'HH:mm"));
    onToChange("");
  };

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
      />
      <Input
        className={classes.input}
        label={label && `${label} - To`}
        value={to}
        onChange={onToChange}
        type="datetime-local"
        fullWidth
        clearable
      />
      <FormControl className={classes.quick}>
        <InputLabel>Quick Range</InputLabel>
        <ButtonGroup
          options={[
            { label: "3H", onClick: () => quickRange(3) },
            { label: "1D", onClick: () => quickRange(24) },
            { label: "7D", onClick: () => quickRange(168) },
          ]}
        />
      </FormControl>
    </div>
  );
}

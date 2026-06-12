import React from "react";
import { makeStyles } from "@material-ui/styles";
import clsx from "clsx";
import Paper from "@material-ui/core/Paper";

const useStyles = makeStyles({
  padded: {
    padding: 15,
  },
});

export default React.forwardRef(function (
  { elevation, className, padded, ...props },
  ref
) {
  const classes = useStyles();
  const internalClassName = [];
  if (padded) internalClassName.push(classes.padded);
  return (
    <Paper
      ref={ref}
      elevation={elevation || 0}
      className={clsx([internalClassName, className])}
      {...props}
    />
  );
});

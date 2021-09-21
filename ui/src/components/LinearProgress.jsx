import React from "react";
import { makeStyles } from "@material-ui/styles";
import clsx from "clsx";
import LinearProgress from "@material-ui/core/LinearProgress";

const useStyles = makeStyles({
  progress: {
    marginBottom: -4,
    zIndex: 999,
  },
});

export default function ({ className, ...props }) {
  const classes = useStyles();

  return (
    <LinearProgress
      className={clsx([classes.progress, className])}
      {...props}
    />
  );
}

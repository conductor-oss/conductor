import React from "react";
import { Paper } from "@material-ui/core";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  root: {
    padding: 15,
    backgroundColor: "rgba(73, 105, 228, 0.1)",
    color: "rgba(0, 0, 0, 0.9)",
    borderLeft: "solid rgba(73, 105, 228, 0.1) 4px",
  },
});

export default function Banner({ children, ...rest }) {
  const classes = useStyles();

  return (
    <Paper
      elevation={0}
      classes={{
        root: classes.root,
      }}
      {...rest}
    >
      {children}
    </Paper>
  );
}

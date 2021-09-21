import React from "react";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  wrapper: {
    overflowY: "scroll",
    overflowX: "hidden",
    height: "100%",
    padding: 30,
  },
});

export default function Wrapper({ className, ...props }) {
  const classes = useStyles();
  return <div className={classes.wrapper} {...props} />;
}

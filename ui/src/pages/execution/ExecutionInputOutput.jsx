import React from "react";
import { Paper, ReactJson } from "../../components";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  json: {
    margin: "20px 15px 20px 15px",
  },
});

export default function InputOutput({ execution }) {
  const classes = useStyles();
  return (
    <Paper padded>
      <ReactJson className={classes.json} src={execution.input} title="Input" />
      <ReactJson
        className={classes.json}
        src={execution.output}
        title="Output"
      />
      <ReactJson
        className={classes.json}
        src={execution.variables}
        title="Variables"
      />
    </Paper>
  );
}

import React from "react";
import { Paper, ReactJson } from "../../components";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  wrapper: {
    height: "100%",
    display: "flex",
    flexDirection: "column",
    justifyContent: "space-between",
  },
  column: {
    display: "flex",
    flexDirection: "row",
    gap: 15,
    flex: 2,
  },
  paper: {
    flex: 1,
    marginBottom: 15,
    padding: "10px 0 0 0",
  },
});

export default function InputOutput({ execution }) {
  const classes = useStyles();
  return (
    <div className={classes.wrapper}>
      <div className={classes.column}>
        <Paper className={classes.paper}>
          <ReactJson
            className={classes.json}
            src={execution.input}
            label="Input"
          />
        </Paper>
        <Paper className={classes.paper}>
          <ReactJson
            className={classes.json}
            src={execution.output}
            label="Output"
          />
        </Paper>
      </div>
      <Paper className={classes.paper}>
        <ReactJson
          className={classes.json}
          src={execution.variables}
          label="Variables"
        />
      </Paper>
    </div>
  );
}

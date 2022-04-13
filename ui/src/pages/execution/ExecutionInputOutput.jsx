import React from "react";
import { Paper, ReactJson } from "../../components";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  wrapper: {
    margin: 30,
    height: "100%",
    display: "flex",
    flexDirection: "column",
    overflow: "hidden",
  },
  column: {
    display: "flex",
    flexDirection: "row",
    gap: 15,
    flex: 2,
    marginBottom: 15,
    overflow: "hidden",
  },
  paper: {
    flex: 1,
    overflow: "hidden",
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

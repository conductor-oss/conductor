import React from "react";
import { Paper } from "../../components";
import ReactJson from "../../components/ReactJson";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  paper: {
    margin: 30,
    flex: 1,
  },
  wrapper: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
  },
});

export default function ExecutionJson({ execution }) {
  const classes = useStyles();

  return (
    <div className={classes.wrapper}>
      <Paper className={classes.paper}>
        <ReactJson label="Unabridged Workflow JSON" src={execution} />
      </Paper>
    </div>
  );
}

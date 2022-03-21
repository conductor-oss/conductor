import React from "react";
import { Paper } from "../../components";
import ReactJson from "../../components/ReactJson";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  paper: {
    height: "100%",
    padding: "10px 0 0 0",
  },
});

export default function ExecutionJson({ execution }) {
  const classes = useStyles();

  return (
    <Paper className={classes.paper}>
      <ReactJson label="Unabridged Workflow JSON" src={execution} />
    </Paper>
  );
}

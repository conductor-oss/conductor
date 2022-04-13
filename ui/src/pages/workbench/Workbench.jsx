import { useState, useRef } from "react";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import RunHistory from "./RunHistory";
import WorkbenchForm from "./WorkbenchForm";
import { colors } from "../../theme/variables";
import { useStartWorkflow } from "../../data/workflow";
import ExecutionHistory from "./ExecutionHistory";

const useStyles = makeStyles({
  wrapper: {
    height: "100%",
    overflow: "hidden",
    display: "flex",
    flexDirection: "row",
    position: "relative",
  },
  name: {
    width: "50%",
  },
  submitButton: {
    float: "right",
  },
  toolbar: {
    backgroundColor: colors.gray14,
  },
  workflowName: {
    fontWeight: "bold",
  },
  main: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
  },
  row: {
    display: "flex",
    flexDirection: "row",
  },
  fields: {
    margin: 30,
    flex: 1,
    display: "flex",
    flexDirection: "column",
    gap: 15,
  },
  runInfo: {
    marginLeft: -350,
  },
});

export default function Workbench() {
  const classes = useStyles();

  const runHistoryRef = useRef();
  const [run, setRun] = useState(undefined);

  const { mutate: startWorkflow } = useStartWorkflow({
    onSuccess: (workflowId, variables) => {
      runHistoryRef.current.updateRun(variables.createTime, workflowId);
    },
  });

  const handleRunSelect = (run) => {
    setRun(run);
  };

  const handleSaveRun = (runPayload) => {
    const newRun = runHistoryRef.current.pushNewRun(runPayload);
    setRun(newRun);
    return newRun;
  };

  const handleExecuteRun = (createTime, runPayload) => {
    startWorkflow({
      createTime,
      body: runPayload,
    });
  };

  return (
    <>
      <Helmet>
        <title>Conductor UI - Workbench</title>
      </Helmet>

      <div className={classes.wrapper}>
        <RunHistory ref={runHistoryRef} onRunSelected={handleRunSelect} />

        <WorkbenchForm
          selectedRun={run}
          saveRun={handleSaveRun}
          executeRun={handleExecuteRun}
        />
        <ExecutionHistory run={run} />
      </div>
    </>
  );
}

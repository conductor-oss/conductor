import React from "react";
import { makeStyles } from "@material-ui/styles";
import { isFailedTask } from "../../utils/helpers";
import { PrimaryButton, DropdownButton } from "../../components";

import StopIcon from "@material-ui/icons/Stop";
import PauseIcon from "@material-ui/icons/Pause";
import RestartIcon from "@material-ui/icons/SettingsBackupRestore";
import ReplayIcon from "@material-ui/icons/Replay";
import ResumeIcon from "@material-ui/icons/PlayArrow";
import FlareIcon from "@material-ui/icons/Flare";

import { useAction } from "../../utils/query";

const useStyles = makeStyles({
  menuIcon: {
    marginRight: 10,
  },
});

export default function ActionModule({ execution, triggerReload }) {
  const classes = useStyles();
  const { workflowId, workflowDefinition } = execution;

  const restartAction = useAction(
    `/workflow/${workflowId}/restart`,
    "post",
    { onSuccess }
  );
  const restartLatestAction = useAction(
    `/workflow/${workflowId}/restart?useLatestDefinitions=true`,
    "post",
    { onSuccess }
  );
  const retryAction = useAction(
    `/workflow/${workflowId}/retry?resumeSubworkflowTasks=false`,
    "post",
    { onSuccess }
  );
  const retryResumeSubworkflowTasksAction = useAction(
    `/workflow/${workflowId}/retry?resumeSubworkflowTasks=true`,
    "post",
    { onSuccess }
  );
  const terminateAction = useAction(
    `/workflow/${workflowId}`,
    "delete",
    { onSuccess }
  );
  const resumeAction = useAction(
    `/workflow/${workflowId}/resume`,
    "put",
    { onSuccess }
  );
  const pauseAction = useAction(
    `/workflow/${workflowId}/pause`,
    "put",
    { onSuccess }
  );

  const { restartable } = workflowDefinition;

  function onSuccess(data, variables, context) {
    triggerReload();
  }

  if (execution.status === "COMPLETED") {

    const options = [];
    if (restartable) {
      options.push({
        label: (
          <>
            <RestartIcon className={classes.menuIcon} fontSize="small" />
            Restart with Current Definitions
          </>
        ),
        handler: () => restartAction.mutate(),
      });

      options.push({
        label: (
          <>
            <FlareIcon className={classes.menuIcon} fontSize="small" />
            Restart with Latest Definitions
          </>
        ),
        handler: () => restartLatestAction.mutate(),
      });
    }

    return <DropdownButton options={options}>Actions</DropdownButton>;
  } 
  else if (execution.status === "RUNNING") {
    return (
      <DropdownButton
        options={[
          {
            label: (
              <>
                <StopIcon
                  fontSize="small"
                  style={{ color: "red" }}
                  className={classes.menuIcon}
                />
                <span style={{ color: "red" }}>Terminate</span>
              </>
            ),
            handler: () => terminateAction.mutate(),
          },
          {
            label: (
              <>
                <PauseIcon fontSize="small" className={classes.menuIcon} />
                Pause
              </>
            ),
            handler: () => pauseAction.mutate(),
          },
        ]}
      >
        Actions
      </DropdownButton>
    );
  } else if (execution.status === "PAUSED") {
    return (
      <PrimaryButton onClick={() => resumeAction.mutate()}>
        <ResumeIcon fontSize="small" />
        Resume
      </PrimaryButton>
    );
  } else {
    // FAILED, TIMED_OUT, TERMINATED
    const options = [];
    if (restartable) {
      options.push({
        label: (
          <>
            <RestartIcon className={classes.menuIcon} fontSize="small" />
            Restart with Current Definitions
          </>
        ),
        handler: () => restartAction.mutate(),
      });

      options.push({
        label: (
          <>
            <FlareIcon className={classes.menuIcon} fontSize="small" />
            Restart with Latest Definitions
          </>
        ),
        handler: () => restartLatestAction.mutate(),
      });
    }

    options.push({
      label: (
        <>
          <ReplayIcon className={classes.menuIcon} fontSize="small" />
          Retry - From failed task
        </>
      ),
      handler: () => retryAction.mutate(),
    });

    if (
      (execution.status === "FAILED" || execution.status === "TIMED_OUT") &&
      execution.tasks.find(
        (task) =>
          task.workflowTask.type === "SUB_WORKFLOW" && isFailedTask(task.status)
      )
    ) {
      options.push({
        label: (
          <>
            <ReplayIcon className={classes.menuIcon} fontSize="small" />
            Retry - Resume subworkflow
          </>
        ),
        handler: () => retryResumeSubworkflowTasksAction.mutate(),
      });
    }

    return <DropdownButton options={options}>Actions</DropdownButton>;
  }
}

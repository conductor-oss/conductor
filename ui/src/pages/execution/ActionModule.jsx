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

import {
  useRestartAction,
  useRestartLatestAction,
  useResumeAction,
  useRetryResumeSubworkflowTasksAction,
  useRetryAction,
  useTerminateAction,
  usePauseAction,
} from "../../data/actions";

const useStyles = makeStyles({
  menuIcon: {
    marginRight: 10,
  },
});

export default function ActionModule({ execution, triggerReload }) {
  const classes = useStyles();
  const { workflowId, workflowDefinition } = execution;

  const restartAction = useRestartAction({ workflowId, onSuccess });
  const restartLatestAction = useRestartLatestAction({ workflowId, onSuccess });
  const retryAction = useRetryAction({ workflowId, onSuccess });
  const retryResumeSubworkflowTasksAction =
    useRetryResumeSubworkflowTasksAction({ workflowId, onSuccess });
  const terminateAction = useTerminateAction({ workflowId, onSuccess });
  const resumeAction = useResumeAction({ workflowId, onSuccess });
  const pauseAction = usePauseAction({ workflowId, onSuccess });

  const { restartable } = workflowDefinition;

  function onSuccess() {
    triggerReload();
  }

  if (execution.status === "COMPLETED") {
    const options = [];
    if (restartable) {
      options.push({
        label: (
          <>
            <RestartIcon className={classes.menuIcon} />
            Restart with Current Definitions
          </>
        ),
        handler: () => restartAction.mutate(),
      });

      options.push({
        label: (
          <>
            <FlareIcon className={classes.menuIcon} />
            Restart with Latest Definitions
          </>
        ),
        handler: () => restartLatestAction.mutate(),
      });
    }

    return <DropdownButton options={options}>Actions</DropdownButton>;
  } else if (execution.status === "RUNNING") {
    return (
      <DropdownButton
        options={[
          {
            label: (
              <>
                <StopIcon
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
                <PauseIcon className={classes.menuIcon} />
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
        <ResumeIcon />
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
            <RestartIcon className={classes.menuIcon} />
            Restart with Current Definitions
          </>
        ),
        handler: () => restartAction.mutate(),
      });

      options.push({
        label: (
          <>
            <FlareIcon className={classes.menuIcon} />
            Restart with Latest Definitions
          </>
        ),
        handler: () => restartLatestAction.mutate(),
      });
    }

    options.push({
      label: (
        <>
          <ReplayIcon className={classes.menuIcon} />
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
            <ReplayIcon className={classes.menuIcon} />
            Retry - Resume subworkflow
          </>
        ),
        handler: () => retryResumeSubworkflowTasksAction.mutate(),
      });
    }

    return <DropdownButton options={options}>Actions</DropdownButton>;
  }
}

import React from "react";
import { makeStyles } from "@material-ui/styles";
import { isFailedTask } from "../../utils/helpers";
import { PrimaryButton, DropdownButton } from "../../components";

import StopIcon from "@material-ui/icons/Stop";
import PauseIcon from "@material-ui/icons/Pause";
import RewindIcon from "@material-ui/icons/FastRewind";
import ReplayIcon from "@material-ui/icons/Replay";
import ResumeIcon from "@material-ui/icons/PlayArrow";
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
    onSuccess
  );
  const retryAction = useAction(
    `/workflow/${workflowId}/retry?resumeSubworkflowTasks=false`,
    "post",
    onSuccess
  );
  const retryResumeSubworkflowTasksAction = useAction(
    `/workflow/${workflowId}/retry?resumeSubworkflowTasks=true`,
    "post",
    onSuccess
  );
  const terminateAction = useAction(
    `/workflow/${workflowId}`,
    "delete",
    onSuccess
  );
  const resumeAction = useAction(
    `/workflow/${workflowId}/resume`,
    "put",
    onSuccess
  );
  const pauseAction = useAction(
    `/workflow/${workflowId}/pause`,
    "put",
    onSuccess
  );

  const { restartable } = workflowDefinition;

  function onSuccess(data, variables, context) {
    console.log(data, variables, context);
    triggerReload();
  }

  if (execution.status === "COMPLETED") {
    return (
      <PrimaryButton
        onClick={() => restartAction.mutate()}
        className={classes.menuIcon}
      >
        <RewindIcon fontSize="small" /> Restart
      </PrimaryButton>
    );
  } else if (execution.status === "RUNNING") {
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
            <RewindIcon className={classes.menuIcon} fontSize="small" />
            Restart workflow
          </>
        ),
        handler: () => restartAction.mutate(),
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

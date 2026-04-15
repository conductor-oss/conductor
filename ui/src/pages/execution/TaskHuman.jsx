import React from "react";
import { useUpdateTask } from "../../data/task";
import TaskHumanForm from "./TaskHumanForm";
import { makeStyles } from "@material-ui/styles";

const useStyles = makeStyles({
  wrapper: {
    height: "100%",
    overflow: "hidden",
    display: "flex",
    flexDirection: "column",
    position: "relative",
  }
});

export default function TaskHuman({ taskResult, onTaskExecuted }) {
  const classes = useStyles();

  const { mutate: updateTask } = useUpdateTask({
    onSuccess: () => {
      console.log("Successfully updated task");
      onTaskExecuted();
    },
  });

  const completeTask = (payload) => {
    updateTask({
      body: payload
    });
  }

  const initData = {
    workflowInstanceId: taskResult.workflowInstanceId,
    taskId: taskResult.taskId
  }

  return (
    <div className={classes.wrapper}>
      <TaskHumanForm
        initialData={initData}
        completeTask={completeTask}
      />
    </div>
  );
}
export const stressGraph = (workflowDefinition, repetitions) => {
  const newWorkflow = { ...workflowDefinition };
  newWorkflow.tasks = repeatTasks(newWorkflow.tasks, repetitions);
  return newWorkflow;
};

const repeatTasks = (tasks, repetitions) =>
  Array.from({ length: repetitions }, (v, i) => {
    return tasks.map((task) => {
      const newTask = { ...task };
      newTask.name = `${task.name}_${i}`;
      newTask.taskReferenceName = `${task.taskReferenceName}_${i}`;

      if (task.forkTasks) {
        newTask.forkTasks = task.forkTasks.map((forkTask) => {
          const newForkTask = forkTask.map((forkTaskItem) => {
            const newForkTaskItem = { ...forkTaskItem };
            newForkTaskItem.name = `${forkTaskItem.name}_${i}`;
            newForkTaskItem.taskReferenceName = `${forkTaskItem.taskReferenceName}_${i}`;

            if (forkTaskItem.loopOver) {
              newForkTaskItem.loopOver = forkTaskItem.loopOver.map(
                (loopOverItem) => {
                  const newLoopOverItem = { ...loopOverItem };
                  newLoopOverItem.name = `${loopOverItem.name}_${i}`;
                  newLoopOverItem.taskReferenceName = `${loopOverItem.taskReferenceName}_${i}`;
                  return newLoopOverItem;
                },
              );
            }

            return newForkTaskItem;
          });
          return newForkTask;
        });

        if (task.joinOn?.length > 0) {
          newTask.joinOn = task.joinOn.map((name) => {
            return `${name}_${i}`;
          });
        }
      }
      return newTask;
    });
  }).flat();

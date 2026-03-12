import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import { TestTaskButtonTypes, TestTaskButtonEvents } from "./types";

export const useTestTaskButtonMachine = (
  actor: ActorRef<TestTaskButtonEvents>,
) => {
  const originalTask = useSelector(
    actor,
    (state) => state.context.originalTask,
  );
  const taskChanges = useSelector(actor, (state) => state.context.taskChanges);
  const taskDomain = useSelector(actor, (state) => state.context.taskDomain);
  const testedTaskExecutionResult = useSelector(
    actor,
    (state) => state.context.testedTaskExecutionResult,
  );
  const testExecutionId = useSelector(
    actor,
    (state) => state.context.testExecutionId,
  );

  const setInputParameters = (inputParameters: Record<string, unknown>) => {
    actor.send({
      type: TestTaskButtonTypes.UPDATE_TASK_VARIABLES,
      inputParameters,
    });
  };
  const setTaskDomain = (domain: string) => {
    actor.send({
      type: TestTaskButtonTypes.SET_TASK_DOMAIN,
      domain,
    });
  };
  const handleRunTestTask = () => {
    actor.send({
      type: TestTaskButtonTypes.TEST_TASK,
    });
  };

  return [
    {
      originalTask,
      taskChanges,
      taskDomain,
      testedTaskExecutionResult,
      testExecutionId,
    },
    {
      setInputParameters,
      setTaskDomain,
      handleRunTestTask,
    },
  ] as const;
};

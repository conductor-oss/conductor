import { TestTask } from "components/v1/TestTask";
import { useInterpret, useSelector } from "@xstate/react";
import { TestTaskButtonMachineStates, TestTaskMachine } from "./state";
import { useAuthHeaders } from "utils/query";
import { useTestTaskButtonMachine } from "./state/hook";
import { useAuth } from "shared/auth";
import { useContext } from "react";
import { MessageContext } from "components/v1/layout/MessageContext";
import { TestTaskButtonProps } from "types/TestTaskTypes";

export const TestTaskButton = ({
  task,
  maxHeight,
  onDismiss,
  showForm,
  tasksList,
}: TestTaskButtonProps) => {
  const authHeaders = useAuthHeaders();
  const { conductorUser } = useAuth();
  const { setMessage } = useContext(MessageContext);

  const testTaskActor = useInterpret(TestTaskMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
      user: conductorUser,
      originalTask: task,
      taskChanges: task?.inputParameters,
      tasksList: tasksList,
    },
    actions: {
      setErrorMessage: (__, data: any) => {
        setMessage({
          text: data?.data?.message,
          severity: "error",
        });
      },
    },
  });

  const [
    { taskChanges, taskDomain, testExecutionId, testedTaskExecutionResult },
    { setInputParameters, setTaskDomain, handleRunTestTask },
  ] = useTestTaskButtonMachine(testTaskActor);

  const isInProgress = useSelector(testTaskActor, (state) => {
    return (
      state.matches([
        TestTaskButtonMachineStates.RUN_TEST_TASK,
        "runTestTask",
      ]) ||
      state.matches([
        TestTaskButtonMachineStates.RUN_TEST_TASK,
        "pollForExecutionResult",
      ])
    );
  });

  return (
    <TestTask
      taskModel={task?.inputParameters || {}}
      onChangeModel={(value) => setInputParameters(value)}
      domain={taskDomain}
      onChangeDomain={(value) => setTaskDomain(value)}
      value={taskChanges}
      maxHeight={maxHeight}
      handleRunTestTask={handleRunTestTask}
      testExecutionId={testExecutionId}
      onDismiss={onDismiss}
      testedTaskExecutionResult={testedTaskExecutionResult}
      isInProgress={isInProgress}
      showForm={showForm}
    />
  );
};

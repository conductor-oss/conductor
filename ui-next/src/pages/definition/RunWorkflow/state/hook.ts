import { ActorRef, State } from "xstate";
import { useSelector } from "@xstate/react";
import {
  RunMachineContext,
  RunMachineEvents,
  RunMachineEventsTypes,
  RunMachineStates,
  FieldsData,
  IdempotencyStrategyEnum,
  IdempotencyValuesProp,
} from "./types";
import { PopoverMessage } from "types/Messages";

export const useRunTabActor = (actor: ActorRef<RunMachineEvents>) => {
  const currentWf = useSelector(
    actor,
    (state: State<RunMachineContext>) => state.context.currentWf,
  );
  const input = useSelector(
    actor,
    (state: State<RunMachineContext>) => state.context.input,
  );
  const correlationId = useSelector(
    actor,
    (state: State<RunMachineContext>) => state.context.correlationId,
  );
  const idempotencyKey = useSelector(
    actor,
    (state: State<RunMachineContext>) => state.context.idempotencyKey,
  );
  const idempotencyStrategy = useSelector(
    actor,
    (state: State<RunMachineContext>) => state.context.idempotencyStrategy,
  );

  const taskToDomain = useSelector(
    actor,
    (state: State<RunMachineContext>) => state.context.taskToDomain,
  );

  const isRunning = useSelector(actor, (state) =>
    state.matches(RunMachineStates.RUN_WORKFLOW),
  );
  const popoverMessage = useSelector(
    actor,
    (state: State<RunMachineContext>) => state.context.popoverMessage,
  );
  const handleChangeInputParams = (changes: string) => {
    actor.send({
      type: RunMachineEventsTypes.UPDATE_INPUT_PARAMS,
      changes,
    });
  };
  const handleChangeCorrelationId = (changes: string) => {
    actor.send({
      type: RunMachineEventsTypes.UPDATE_CORRELATION_ID,
      changes,
    });
  };
  const handleChangeIdempotencyKey = (changes: string) => {
    actor.send({
      type: RunMachineEventsTypes.UPDATE_IDEMPOTENCY_KEY,
      changes,
    });
  };
  const handleChangeIdempotencyStrategy = (
    changes: IdempotencyStrategyEnum,
  ) => {
    actor.send({
      type: RunMachineEventsTypes.UPDATE_IDEMPOTENCY_STRATEGY,
      changes,
    });
  };

  const handleChangeIdempotencyValues = (changes: IdempotencyValuesProp) => {
    actor.send({
      type: RunMachineEventsTypes.UPDATE_IDEMPOTENCY_VALUES,
      changes,
    });
  };
  const handleChangeTasksToDomain = (changes: string) => {
    actor.send({
      type: RunMachineEventsTypes.UPDATE_TASKS_TO_DOMAIN_MAPPING,
      changes,
    });
  };
  const handleClearForm = () => {
    actor.send({
      type: RunMachineEventsTypes.CLEAR_FORM,
    });
  };
  const handleRunThisWorkflow = () => {
    actor.send({
      type: RunMachineEventsTypes.TRIGGER_RUN_WORKFLOW,
    });
  };

  const handlePopoverMessage = (popoverMessage: PopoverMessage | null) => {
    actor.send({
      type: RunMachineEventsTypes.HANDLE_POPOVER_MESSAGE,
      popoverMessage,
    });
  };

  const handleFillAllFields = (data: FieldsData) => {
    actor.send({
      type: RunMachineEventsTypes.UPDATE_ALL_FIELDS,
      data,
    });
  };

  return [
    {
      currentWf,
      input,
      correlationId,
      taskToDomain,
      isRunning,
      popoverMessage,
      idempotencyKey,
      idempotencyStrategy,
    },
    {
      handleChangeInputParams,
      handleChangeCorrelationId,
      handleChangeTasksToDomain,
      handleClearForm,
      handleRunThisWorkflow,
      handlePopoverMessage,
      handleFillAllFields,
      handleChangeIdempotencyKey,
      handleChangeIdempotencyStrategy,
      handleChangeIdempotencyValues,
    },
  ] as const;
};

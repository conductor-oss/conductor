import { TaskStatsMachineContext, UpdateTaskNameEvent } from "./types";

export const nameChanged = (
  { taskName }: TaskStatsMachineContext,
  { name }: UpdateTaskNameEvent,
) => taskName !== name;

import { TaskStatsMachineContext, UpdateTaskNameEvent } from "./types";
export declare const nameChanged: ({ taskName }: TaskStatsMachineContext, { name }: UpdateTaskNameEvent) => boolean;

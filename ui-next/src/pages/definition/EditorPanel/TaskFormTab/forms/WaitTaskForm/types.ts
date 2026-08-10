import { TaskFormProps } from "../types";
import { WaitTaskDef } from "types/TaskType";

export interface WaitTaskFormProps extends TaskFormProps {
  task: WaitTaskDef;
}

export enum WaitType {
  UNTIL = "until",
  DURATION = "duration",
  SIGNAL = "signal",
}

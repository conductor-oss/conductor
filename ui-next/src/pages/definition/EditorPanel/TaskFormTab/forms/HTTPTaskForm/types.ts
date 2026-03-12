import { TaskFormProps } from "../types";
import { HttpTaskDef } from "types";

export interface HttpTaskFormProps extends TaskFormProps {
  task: HttpTaskDef;
}

import { TaskFormProps } from "../types";
import { GrpcTaskDef } from "types";

export interface GrpcTaskFormProps extends TaskFormProps {
  task: GrpcTaskDef;
}

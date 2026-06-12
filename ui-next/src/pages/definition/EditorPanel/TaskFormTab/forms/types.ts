import { TaskDef } from "types";
import { ActorRef } from "xstate";
import { TaskHeaderMachineEvents } from "./TaskFormHeader/state";

export interface TaskFormProps {
  task: Partial<TaskDef>;
  onChange: any;
  updateAdditionalFieldMetadata?: any;
  additionalFieldMetadata?: any;
  isMetaBarEditing?: boolean;
  onToggleExpand?: any;
  collapseWorkflowList?: string[];
  taskFormHeaderActor?: ActorRef<TaskHeaderMachineEvents>;
}

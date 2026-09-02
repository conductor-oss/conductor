import { TaskFormEvents } from "pages/definition/EditorPanel/TaskFormTab/state";
import { WorkflowDefinitionEvents } from "pages/definition/state/types";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
export interface TaskFormProps {
    formTaskActor: ActorRef<TaskFormEvents>;
}
declare const MaybeTaskForm: FunctionComponent<{
    workflowDefinitionActor: ActorRef<WorkflowDefinitionEvents>;
    isInTaskFormState: boolean;
}>;
export default MaybeTaskForm;

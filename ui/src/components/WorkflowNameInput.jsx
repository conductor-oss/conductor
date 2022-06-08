import { useWorkflowNames } from "../data/workflow";
import { Dropdown } from ".";
import { isEmpty } from "lodash";

export default function WorkflowNameInput(props) {
  const workflowNames = useWorkflowNames();

  return (
    <Dropdown
      label={props.label || "Workflow Name"}
      options={workflowNames}
      multiple
      freeSolo
      loading={isEmpty(workflowNames)}
      {...props}
    />
  );
}

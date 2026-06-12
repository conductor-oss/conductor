import { Dropdown } from ".";
import { isEmpty } from "lodash";
import { useTaskNames } from "../data/task";

export default function TaskNameInput(props) {
  const taskNames = useTaskNames();

  return (
    <Dropdown
      label={props.label || "Task Name"}
      options={taskNames}
      multiple
      freeSolo
      loading={isEmpty(taskNames)}
      {...props}
    />
  );
}

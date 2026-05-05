import { useScheduleNames } from "../data/scheduler";
import { Dropdown } from ".";
import { isEmpty } from "lodash";

export default function ScheduleNameInput(props) {
  const scheduleNames = useScheduleNames();

  return (
    <Dropdown
      label={props.label || "Schedule Name"}
      options={scheduleNames}
      multiple
      freeSolo
      loading={isEmpty(scheduleNames)}
      {...props}
    />
  );
}

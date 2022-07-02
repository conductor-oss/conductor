import { useField } from "formik";
import { useWorkflowNames } from "../..//data/workflow";
import { Dropdown } from "../";
import { isEmpty } from "lodash";

export default function (props) {
  const [field, meta, helper] = useField(props);
  const touchedError = meta.touched && meta.error;

  const workflowNames = useWorkflowNames();

  return (
    <Dropdown
      disableClearable
      label={props.label || "Workflow Name"}
      options={workflowNames}
      placeholder={"Select Workflow Name"}
      loading={isEmpty(workflowNames)}
      error={touchedError}
      helperText={touchedError}
      {...field}
      {...props}
      onChange={(e, value) => helper.setValue(value)}
    />
  );
}

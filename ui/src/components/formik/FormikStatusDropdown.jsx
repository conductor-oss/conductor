import { useField } from "formik";
import { Dropdown } from "../index";

export default function (props) {
  const [field, meta, helper] = useField(props);
  const touchedError = meta.touched && meta.error;

  return (
    <Dropdown
      disableClearable
      label={props.label || "Status"}
      options={["COMPLETED", "FAILED"]}
      placeholder={"Select Workflow Name"}
      error={touchedError}
      helperText={touchedError}
      {...field}
      {...props}
      onChange={(e, value) => helper.setValue(value)}
    />
  );
}

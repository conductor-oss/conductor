import { useField } from "formik";
import { Dropdown } from "..";

export default function (props) {
  const [field, meta, helper] = useField(props);
  const touchedError = meta.touched && meta.error;

  return (
    <Dropdown
      {...props}
      {...field}
      onChange={(e, value) => helper.setValue(value)}
      error={touchedError}
      helperText={touchedError}
    />
  );
}

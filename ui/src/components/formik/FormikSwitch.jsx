import React from "react";
import { Switch, InputLabel } from "@material-ui/core";
import { useField } from "formik";

export default function ({ label, ...props }) {
  const [field /*meta*/, , helper] = useField(props);

  return (
    <div>
      <InputLabel variant="outlined">{label}</InputLabel>
      <Switch
        color="primary"
        checked={field.value}
        onChange={(e) => helper.setValue(e.target.checked)}
        {...props}
      />
    </div>
  );
}

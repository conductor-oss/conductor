import React from "react";
import { TextField } from "@material-ui/core";
import { useField } from "formik";

export default function (props) {
  const [field, meta] = useField(props);

  return (
    <TextField
      error={!!(meta.touched && meta.error)}
      helperText={meta.touched && meta.error}
      {...field}
      {...props}
    />
  );
}

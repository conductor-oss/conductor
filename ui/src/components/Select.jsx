import React from "react";
import Select from "@material-ui/core/Select";
import FormControl from "@material-ui/core/FormControl";
import InputLabel from "@material-ui/core/InputLabel";
import _ from "lodash";

export default function ({ label, fullWidth, nullable = true, ...props }) {
  return (
    <FormControl fullWidth={fullWidth}>
      {label && <InputLabel>{label}</InputLabel>}
      <Select
        variant="outlined"
        fullWidth={fullWidth}
        displayEmpty={nullable}
        renderValue={(v) => (_.isNil(v) ? "" : v)}
        {...props}
      />
    </FormControl>
  );
}

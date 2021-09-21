import React from "react";
import { Input } from "./";
import Autocomplete from "@material-ui/lab/Autocomplete";
import FormControl from "@material-ui/core/FormControl";
import InputLabel from "@material-ui/core/InputLabel";

export default function ({ label, className, style, ...props }) {
  return (
    <FormControl style={style} className={className}>
      <InputLabel>{label}</InputLabel>
      <Autocomplete
        renderInput={(params) => <Input {...params} />}
        {...props}
      />
    </FormControl>
  );
}

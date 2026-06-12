import React from "react";
import { InputLabel } from "@material-ui/core";
import { useField } from "formik";
import Cron from "react-cron-generator";

import "./cron.css";

export default function (props) {
  const [field /*meta*/, , helper] = useField(props);

  return (
    <div className={props.className}>
      <InputLabel variant="outlined">Cron Expression</InputLabel>
      <Cron
        value={field.value}
        onChange={(value) => helper.setValue(value)}
        showResultText={true}
        showResultCron={true}
      />
    </div>
  );
}

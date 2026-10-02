import { Grid } from "@mui/material";
import { ConductorUpdateTaskFormEvent } from "components/inputs/ConductorUpdateTaskFromEvent";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { Props } from "./common";

export const FailTask = ({ index, payload, handleChangeAction }: Props) => {
  const { fail_task } = payload;

  return (
    <Grid container spacing={4} sx={{ width: "100%" }}>
      <Grid size={12}>
        <ConductorUpdateTaskFormEvent
          value={fail_task}
          onChange={(upCt) => {
            handleChangeAction(index, {
              ...payload,
              fail_task: { ...upCt, output: fail_task?.output },
            });
          }}
        />
      </Grid>
      <Grid size={12}>
        <ConductorFlatMapFormBase
          onChange={(newValues) => {
            handleChangeAction(index, {
              ...payload,
              fail_task: {
                ...fail_task,
                output: newValues,
              },
            });
          }}
          value={{ ...fail_task?.output }}
          title="Output"
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add parameter"
          showFieldTypes
          enableAutocomplete={false}
          autoFocusField={false}
        />
      </Grid>
    </Grid>
  );
};

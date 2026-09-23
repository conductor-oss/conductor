import { Grid } from "@mui/material";
import { ConductorUpdateTaskFormEvent } from "components/inputs/ConductorUpdateTaskFromEvent";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { Props } from "./common";

export const CompleteTask = ({ index, payload, handleChangeAction }: Props) => {
  const { complete_task } = payload;

  return (
    <Grid container spacing={4} sx={{ width: "100%" }}>
      <Grid size={12}>
        <ConductorUpdateTaskFormEvent
          value={complete_task}
          onChange={(upCt) => {
            handleChangeAction(index, {
              ...payload,
              complete_task: { ...upCt, output: complete_task?.output },
            });
          }}
        />
      </Grid>
      <Grid size={12}>
        <ConductorFlatMapFormBase
          onChange={(newValues) => {
            handleChangeAction(index, {
              ...payload,
              complete_task: {
                ...complete_task,
                output: newValues,
              },
            });
          }}
          value={{ ...complete_task?.output }}
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

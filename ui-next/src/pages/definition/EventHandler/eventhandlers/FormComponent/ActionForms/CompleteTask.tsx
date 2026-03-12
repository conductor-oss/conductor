import { Grid } from "@mui/material";
import IconButton from "components/MuiIconButton";
import MuiTypography from "components/MuiTypography";
import { ConductorUpdateTaskFormEvent } from "components/v1/ConductorUpdateTaskFromEvent";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import XCloseIcon from "components/v1/icons/XCloseIcon";
import { Props } from "./common";

export const CompleteTask = ({
  onRemove,
  index,
  payload,
  handleChangeAction,
}: Props) => {
  const { complete_task } = payload;

  return (
    <Grid
      container
      spacing={4}
      my={2}
      sx={{
        width: "100%",
        position: "relative",
      }}
    >
      <Grid size={12} sx={{ mt: 4 }}>
        <MuiTypography fontWeight={800} fontSize={16}>
          Complete Task
        </MuiTypography>
      </Grid>
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
      <IconButton onClick={onRemove} sx={{ position: "absolute", right: 0 }}>
        <XCloseIcon size={26} />
      </IconButton>
    </Grid>
  );
};

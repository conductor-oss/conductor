import { Grid } from "@mui/material";
import HelperText from "components/ui/inputs/HelperText";
import IconButton from "components/ui/buttons/MuiIconButton";
import MuiTypography from "components/ui/MuiTypography";
import { ConductorUpdateTaskFormEvent } from "components/inputs/ConductorUpdateTaskFromEvent";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import XCloseIcon from "components/icons/XCloseIcon";
import { Props } from "./common";

export const FailTask = ({
  onRemove,
  index,
  payload,
  handleChangeAction,
}: Props) => {
  const { fail_task } = payload;

  return (
    <Grid
      container
      spacing={4}
      my={2}
      sx={{ width: "100%", position: "relative" }}
    >
      <Grid size={12}>
        <MuiTypography fontWeight={800} fontSize={16}>
          Fail Task
        </MuiTypography>
        <HelperText>Choose between one of these options</HelperText>
      </Grid>
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
      <Grid size={12}></Grid>
      <Grid size={12}></Grid>
      <IconButton onClick={onRemove} sx={{ position: "absolute", right: 0 }}>
        <XCloseIcon size={26} />
      </IconButton>
    </Grid>
  );
};

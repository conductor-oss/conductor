import CancelOutlinedIcon from "@mui/icons-material/CancelOutlined";
import { Chip } from "@mui/material";
import { WorkflowExecutionStatus } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import { getChipStatusColor } from "utils/helpers";
import { capitalizeFirstLetter } from "utils/utils";

export const renderStatusTagChip = (value: string[], getTagProps: any) =>
  value.map((val: string | { label: string }, index) => {
    const chipBackground =
      getChipStatusColor(val as TaskStatus | WorkflowExecutionStatus) || {};
    const renderableLabel: string =
      typeof val === "string" || typeof val === "number" ? val : val.label;

    const { key, ...otherTagProps } = getTagProps({ index });
    return (
      <Chip
        key={key}
        label={capitalizeFirstLetter(renderableLabel)}
        {...otherTagProps}
        sx={{
          marginTop: "1px",
          marginBottom: "1px",
          backgroundColor: chipBackground,
          color: "#000",
          borderRadius: "30px",
          "& .MuiSvgIcon-root": {
            background: "transparent",
            fill: "black",
          },
        }}
        deleteIcon={<CancelOutlinedIcon />}
      />
    );
  });

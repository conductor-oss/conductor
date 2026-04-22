import { FunctionComponent } from "react";
import { Stack } from "@mui/material";
import { TaskDefinitionDto } from "types";
import MuiTypography from "components/ui/MuiTypography";
import { FORMAT_TIME_TO_LONG } from "utils/constants/common";
import { formatInTimeZone } from "utils/date";
import _isUndefined from "lodash/isUndefined";

export interface CreationInfoProps {
  task: Partial<TaskDefinitionDto>;
}

export const CreationInfo: FunctionComponent<CreationInfoProps> = ({
  task,
}) => {
  return (
    <Stack spacing={1}>
      {(!_isUndefined(task?.createTime) || !_isUndefined(task?.createdBy)) && (
        <MuiTypography>
          {`Created At ${
            task.createTime
              ? formatInTimeZone(new Date(task.createTime), FORMAT_TIME_TO_LONG)
              : "N/A"
          } by ${task.createdBy || "N/A"}`}
          Created at:&nbsp;
        </MuiTypography>
      )}

      {(!_isUndefined(task.updateTime) || !_isUndefined(task.updatedBy)) && (
        <MuiTypography>
          {`Last updated at ${
            task.updateTime
              ? formatInTimeZone(new Date(task.updateTime), FORMAT_TIME_TO_LONG)
              : "N/A"
          } by ${task.updatedBy || "N/A"}`}
        </MuiTypography>
      )}

      {!_isUndefined(task.ownerEmail) && (
        <MuiTypography>{`Owner email: ${
          task.ownerEmail || "N/A"
        }`}</MuiTypography>
      )}
    </Stack>
  );
};

import { Box } from "@mui/material";
import { Input, Typography } from "components";
import ClipboardCopy from "components/ui/ClipboardCopy";
import { useMemo, useState } from "react";
import { TaskLog } from "types";
import { formatToDateTimeString } from "utils/date";
import { ActorRef } from "xstate";
import { RightPanelEvents } from "./RightPanel/state";
import { useRightPanelActor } from "./RightPanel/state/hook";

export interface TaskLogsProps {
  containerQueryState: any;
  rightPanelActor: ActorRef<RightPanelEvents>;
}

export default function TaskLogs({
  containerQueryState,
  rightPanelActor,
}: TaskLogsProps) {
  const [{ taskLogs }] = useRightPanelActor(rightPanelActor);

  const [filteredLogs, setFilteredLogs] = useState<TaskLog[]>([]);
  const [searchValue, setSearchValue] = useState("");

  useMemo(() => {
    setFilteredLogs(() => {
      const tempSearchValue = searchValue.trim().toLowerCase();
      return (
        taskLogs?.reduce((result: TaskLog[], item: TaskLog) => {
          const createdTimeString = formatToDateTimeString(item.createdTime);

          if (
            createdTimeString.includes(tempSearchValue) ||
            item.log?.toLowerCase()?.includes(tempSearchValue)
          ) {
            result.push({
              ...item,
              createdTime: createdTimeString,
            });
          }

          return result;
        }, []) || []
      );
    });
  }, [searchValue, setFilteredLogs, taskLogs]);

  return (
    <Box sx={{ padding: 5 }}>
      {filteredLogs?.length > 0 && (
        <ClipboardCopy
          buttonId="copy-log-btn"
          value={filteredLogs
            .map((taskLog) => `[${taskLog.createdTime}]  ${taskLog.log}`)
            .join("\n")}
          sx={{
            mb: 1,
            pr: 1,
          }}
          iconPlacement="start"
        >
          <Typography fontSize="12px" fontWeight="500">
            Copy logs
          </Typography>
        </ClipboardCopy>
      )}
      <Input
        fullWidth
        placeholder={"Search logs"}
        clearable
        value={searchValue}
        onChange={setSearchValue}
      />

      {filteredLogs?.length > 0 ? (
        <Box
          sx={{
            fontFamily: "Courier, monospace",
            fontSize: "14px",
            lineHeight: "25px",
            marginTop: 2,
            overflowY: "auto",
            maxHeight: `calc(100vh - ${
              containerQueryState["small"] ? 315 : 285
            }px)`,
          }}
        >
          {filteredLogs.map((item: TaskLog, index) => (
            <Box key={index}>
              [<strong>{item.createdTime}</strong>
              ]&nbsp;
              {item.log}
            </Box>
          ))}
        </Box>
      ) : (
        <Typography sx={{ margin: "15px" }} variant="body1">
          No logs available
        </Typography>
      )}
    </Box>
  );
}

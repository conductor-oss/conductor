import { Box, Stack } from "@mui/material";
import { DataTable } from "components";
import { ColumnCustomType, LegacyColumn } from "components/DataTable/types";
import StatusBadge from "components/StatusBadge";
import { FunctionComponent, useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import { ExecutionTask } from "types";
import { calculateDifferentTime } from "utils/utils";
import { ActorRef } from "xstate";
import {
  SelectableStatus,
  TaskListMachineEvents,
  useTaskListActor,
} from "./state";
import { StatusSelect } from "./StatusSelect";
import { clickHandler, taskIdRenderer } from "pages/execution/componentHelpers";

const calculateExecutionTime = (startTime: number, endTime: number) =>
  new Date(endTime).getTime() - new Date(startTime).getTime();

const customSortForExecutionTime = (
  rowA: ExecutionTask,
  rowB: ExecutionTask,
) => {
  const executionTimeA =
    rowA.startTime && rowA.endTime
      ? calculateExecutionTime(rowA.startTime, rowA.endTime)
      : 0;
  const executionTimeB =
    rowB.startTime && rowB.endTime
      ? calculateExecutionTime(rowB.startTime, rowB.endTime)
      : 0;

  return executionTimeA - executionTimeB;
};

export const MIN_DATE_WIDTH = "175px";

interface TaskListProps {
  taskListActor: ActorRef<TaskListMachineEvents>;
  executionAlert: string;
}

export const TaskList: FunctionComponent<TaskListProps> = ({
  taskListActor,
}) => {
  const [
    { taskListPage, statusFilter, totalHits, isFetching, rowsPerPage, summary },
    {
      handleChangeStatus,
      handleChangePage,
      handleChangeRowsPerPage,
      handleSelectTask,
    },
  ] = useTaskListActor(taskListActor);

  const { mode } = useContext(ColorModeContext);
  // const [{ expandDynamic }, { execution }] = useExecutionMachine();
  const taskDetailFields = [
    {
      id: "seq",
      name: "seq",
      label: "Seq.",
      minWidth: "50px",
      maxWidth: "100px",
      style: { whiteSpace: "nowrap", wordBreak: "keep-all" },
      tooltip: "The sequence number of the task",
    },
    {
      id: "taskId",
      name: "taskId",
      label: "Task Id",
      minWidth: "130px",
      maxWidth: "130px",
      renderer: taskIdRenderer(clickHandler(handleSelectTask)),
      tooltip: "The unique identifier of the task",
    },
    {
      id: "taskName",
      name: "workflowTask.name",
      label: "Task Name",
      tooltip: "The name of the task",
    },
    {
      id: "referenceTaskName",
      name: "referenceTaskName",
      label: "Ref",
      minWidth: "250px",
      maxWidth: "350px",
      tooltip: "The Reference Task Name",
    },
    {
      id: "taskType",
      name: "workflowTask.type",
      label: "Type",
      minWidth: "100px",
      maxWidth: "200px",
      tooltip: "The Task type",
    },
    {
      id: "scheduledTime",
      name: "scheduledTime",
      type: ColumnCustomType.DATE,
      label: "Scheduled Time",
      minWidth: MIN_DATE_WIDTH,
      maxWidth: MIN_DATE_WIDTH,
      tooltip: "The time the task was scheduled to run",
    },
    {
      id: "startTime",
      name: "startTime",
      type: ColumnCustomType.DATE,
      label: "Start Time",
      minWidth: MIN_DATE_WIDTH,
      maxWidth: MIN_DATE_WIDTH,
      tooltip: "The time the task started running",
    },
    {
      id: "endTime",
      name: "endTime",
      type: ColumnCustomType.DATE,
      label: "End Time",
      minWidth: MIN_DATE_WIDTH,
      maxWidth: MIN_DATE_WIDTH,
      tooltip: "The time the task ended running",
    },
    {
      id: "executionTime",
      name: "executionTime",
      label: "Execution Time",
      minWidth: "100px",
      maxWidth: "200px",
      renderer: (_: unknown, { startTime, endTime }: ExecutionTask) =>
        startTime && endTime ? calculateDifferentTime(startTime, endTime) : "",
      sortFunction: customSortForExecutionTime,
      tooltip: "The time the task took to run",
    },
    {
      id: "status",
      name: "status",
      grow: 0.5,
      label: "Status",
      minWidth: "120px",
      maxWidth: "150px",
      renderer: (status: SelectableStatus) => <StatusBadge status={status} />,
      tooltip: "The status of the task",
    },
    {
      id: "updateTime",
      name: "updateTime",
      type: ColumnCustomType.DATE,
      label: "Update Time",
      tooltip: "The time the task was last updated",
    },
    {
      id: "callbackAfterSeconds",
      name: "callbackAfterSeconds",
      label: "Callback",
      tooltip: "The time the task should callback",
    },
    {
      id: "pollCount",
      name: "pollCount",
      label: "Poll Count",
      tooltip: "The number of times the task has been polled",
    },
  ];

  return (
    <Stack
      sx={{
        padding: 2,
        height: "100%",
      }}
    >
      <Box
        sx={{
          backgroundColor:
            mode === "dark" ? colors.grayTableBackground : colors.white,
          height: "100%",
        }}
      >
        <DataTable
          fixedHeader
          data={taskListPage}
          customStyles={{
            responsiveWrapper: {
              style: {
                overflowY: "auto",
              },
            },
          }}
          customActions={[
            <StatusSelect
              onSelect={handleChangeStatus!}
              value={statusFilter}
              summary={summary}
              key="status-select"
            />,
          ]}
          columns={taskDetailFields as LegacyColumn[]}
          progressPending={isFetching}
          onChangePage={(page: number) => handleChangePage!(page)}
          onChangeRowsPerPage={(newPerPage: number, _page: number) =>
            handleChangeRowsPerPage!(newPerPage)
          }
          defaultShowColumns={[
            "seq",
            "taskId",
            "referenceTaskName",
            "taskType",
            "startTime",
            "endTime",
            "executionTime",
            "scheduledTime",
            "status",
          ]}
          pagination
          hideSearch
          paginationServer
          paginationPerPage={rowsPerPage}
          paginationRowsPerPageOptions={[20, 50, 100]}
          paginationTotalRows={totalHits}
          localStorageKey="taskListTable"
          sortByDefault={false}
        />
      </Box>
    </Stack>
  );
};

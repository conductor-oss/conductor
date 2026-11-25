import { Box, Grid, Radio } from "@mui/material";
import { useActor, useSelector } from "@xstate/react";
import { DataTable } from "components";
import { useContext } from "react";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { QuickSearchRefresh } from "./QuickSearchAndRefresh";
import { FilterSection } from "./filter";
import {
  createQueueSummaries,
  lastPollTimeColumnRenderer,
  QueueSummary,
} from "./helpers";
import { QueueMachineEventTypes, QueueMonitorContext } from "./state";

const dataColumns: any = [
  {
    name: "queueName",
    id: "queueName",
    label: "Queue Name",
    tooltip: "The name of the queue",
  },
  {
    name: "size",
    id: "size",
    label: "Queue Size",
    maxWidth: "200px",
    tooltip: "The number of items in the queue",
  },
  {
    name: "pollerCount",
    id: "pollerCount",
    label: "Worker Count",
    tooltip: "The number of workers polling the queue",
    maxWidth: "200px",
  },
  {
    name: "lastPollTime",
    id: "lastPollTime",
    label: "Last Poll Time",
    tooltip: "The last time the queue was polled",
    renderer: lastPollTimeColumnRenderer,
  },
];

export const PollDataTable = () => {
  const { queueMachineActor } = useContext(QueueMonitorContext);
  const [
    { pageParam, searchParam },
    { handleSearchTermChange, handlePageChange },
  ] = useCustomPagination();

  const data = useSelector(
    queueMachineActor!,
    ({ context: { pollDataByQueueName = {}, queueData = {} } }) =>
      createQueueSummaries(pollDataByQueueName, queueData),
  );

  const selectedQueueName = useSelector(
    queueMachineActor!,
    (state) => state.context.selectedQueueName,
  );
  const [, send] = useActor(queueMachineActor!);
  const handleSelectRow = (queueName: string) => {
    send({
      type: QueueMachineEventTypes.SELECT_QUEUE_NAME,
      queueName,
    });
  };

  const selectColumn = {
    name: "select",
    id: "select",
    label: "Select",
    maxWidth: "150px",
    tooltip: "Select the queue",
    renderer: (_id: any, rowData: QueueSummary) => {
      return (
        <Radio
          value={rowData.queueName}
          inputProps={{ "aria-label": `Select queue ${rowData.queueName}` }}
          onChange={(_event) => {
            handleSelectRow(rowData.queueName);
          }}
          checked={rowData.queueName === selectedQueueName}
        />
      );
    },
    sortable: false,
  };

  const columns: any = [selectColumn].concat(dataColumns);

  const isLoading = useSelector(
    queueMachineActor!,
    (state) => !state.matches("ready"),
  );

  return (
    <Box>
      <QuickSearchRefresh
        searchTerm={searchParam}
        onChange={handleSearchTermChange}
        label="Quick search"
        quickSearchPlaceholder="Quick search"
      />
      <Box px={6}>
        <FilterSection queueMachineActor={queueMachineActor!} />
      </Box>
      <Grid container sx={{ width: "100%" }} spacing={1}>
        <Grid size={12}>
          <DataTable
            progressPending={isLoading}
            sortByDefault={false}
            quickSearchEnabled
            quickSearchComponent={() => null}
            localStorageKey="pollDataTable"
            noDataComponent={
              <Box padding={5} fontWeight={600}>
                No polling details found
              </Box>
            }
            defaultShowColumns={["select", "queueName", "size", "pollerCount"]}
            data={data}
            columns={columns}
            searchTerm={searchParam}
            onChangePage={handlePageChange}
            paginationDefaultPage={pageParam ? Number(pageParam) : 1}
          />
        </Grid>
      </Grid>
    </Box>
  );
};

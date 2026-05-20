import { Box, Grid, Radio } from "@mui/material";
import { useActor, useSelector } from "@xstate/react";
import { DataTable } from "components";
import { first, last, omit, path } from "lodash/fp";
import { useContext } from "react";
import { Entries } from "types/helperTypes";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { State } from "xstate";
import { QuickSearchRefresh } from "./QuickSearchAndRefresh";
import { FilterSection } from "./filter";
import { lastPollTimeColumnRenderer } from "./helpers";
import {
  PollData,
  QueueMachineEventTypes,
  QueueMonitorContext,
  QueueMonitorMachineContext,
} from "./state";
import { QueueData, QueueSizeCount } from "./state/types";

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

type SelectablePollDataSummary = Partial<PollData> & {
  size: number;
  pollerCount: number;
};

export const PollDataTable = () => {
  const { queueMachineActor } = useContext(QueueMonitorContext);
  const [
    { pageParam, searchParam },
    { handleSearchTermChange, handlePageChange },
  ] = useCustomPagination();

  const data: any = useSelector(
    queueMachineActor!,
    ({
      context: { pollDataByQueueName = {}, queueData = {} },
    }: State<QueueMonitorMachineContext>) => {
      const [usedKeys, activeWorkers] = (
        Object.entries(pollDataByQueueName) as unknown as Array<
          [string, PollData[]]
        >
      ).reduce(
        (
          acc: [string[], SelectablePollDataSummary[]],
          [itemName, pollData]: [string, PollData[]],
        ): [string[], SelectablePollDataSummary[]] => {
          const { size = 0, pollerCount = 0 } = path(itemName, queueData) || {};

          const lastUpdatedPollDataBetweenWorkers =
            first(pollData.sort((pd) => pd.lastPollTime)) || {};

          const usedKeysAcc = (first(acc) as string[]).concat(itemName);

          const selectablePollData: SelectablePollDataSummary = {
            ...lastUpdatedPollDataBetweenWorkers,
            ...{ size, pollerCount },
          };

          const activeWorkeresAcc = (
            last(acc) as SelectablePollDataSummary[]
          ).concat(selectablePollData);

          return [usedKeysAcc, activeWorkeresAcc];
        },
        [[], []],
      );

      const queueDataWithoutPollData: QueueData = omit(
        usedKeys,
        queueData,
      ) as QueueData;
      const inactiveWorkers = (
        Object.entries(queueDataWithoutPollData) as Entries<QueueData>
      ).map(
        // @ts-ignore
        ([k, val = {}]: [
          string,
          QueueSizeCount,
        ]): SelectablePollDataSummary => ({
          queueName: k!,
          pollerCount: 0,
          ...val,
        }),
      );
      return activeWorkers.concat(inactiveWorkers);
    },
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
    renderer: (_id: any, rowData: SelectablePollDataSummary) => {
      return (
        <Radio
          value={rowData.queueName}
          onChange={(_event) => {
            handleSelectRow(rowData.queueName!);
          }}
          checked={rowData.queueName === selectedQueueName}
        />
      );
    },
    sortFunction: (
      rowA: SelectablePollDataSummary,
      rowB: SelectablePollDataSummary,
    ) => {
      if (rowA.queueName === selectedQueueName) {
        return 1;
      }

      if (rowB.queueName === selectedQueueName) {
        return -1;
      }

      return 0;
    },
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

import { Box } from "@mui/material";
import { useSelector } from "@xstate/react";
import { DataTable } from "components";
import { useContext, useEffect, useRef } from "react";
import { pollWorkerColumns } from "./helpers";
import { QueueMonitorContext } from "./state";

export const PollWorkerDetailsDataTable = () => {
  const { queueMachineActor } = useContext(QueueMonitorContext);
  const divRef = useRef<null | HTMLDivElement>(null);
  const [selectedName, noWorkers] = useSelector(queueMachineActor!, (state) => [
    state.context.selectedQueueName,
    state.context.noWorkers,
  ]);
  const data = useSelector(queueMachineActor!, (state) => {
    const queueName = state.context.selectedQueueName;
    return queueName
      ? (state.context.pollDataByQueueName?.[queueName] ?? [])
      : [];
  });
  useEffect(() => {
    if (divRef?.current !== null) {
      divRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [selectedName]);
  return (
    <div ref={divRef}>
      {noWorkers ? (
        <Box
          display="flex"
          justifyContent="center"
          padding={5}
          fontWeight={600}
        >
          There are no polling workers
        </Box>
      ) : (
        <DataTable
          noDataComponent={
            <Box padding={5} fontWeight={600}>
              Details not found
            </Box>
          }
          data={data}
          columns={pollWorkerColumns}
        />
      )}
    </div>
  );
};

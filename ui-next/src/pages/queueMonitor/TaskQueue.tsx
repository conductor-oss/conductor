import { Box, Button } from "@mui/material";
import { useSelector } from "@xstate/react";
import Paper from "components/Paper";
import { Helmet } from "react-helmet";
import { useNavigate } from "react-router";
import SectionContainer from "shared/SectionContainer";
import SectionHeader from "shared/SectionHeader";
import { PollDataTable } from "./PollDataTable";
import { PollWorkerDetailsDataTable } from "./PollWorkerDetails";
import { QueueMonitorContextProvider, useQueueMachine } from "./state";

export default function TaskQueue() {
  const queueMachineActor = useQueueMachine();
  const hasMadeSelection = useSelector(queueMachineActor, (state) =>
    state.matches("ready.tableSelection.withSelection"),
  );
  const showError = useSelector(queueMachineActor, (state) =>
    state.matches("showError"),
  );
  const errorMessage = useSelector(
    queueMachineActor,
    (state) => state.context.errorMessage,
  );

  const navigate = useNavigate();
  return (
    <>
      <Helmet>
        <title>Task Queues Monitoring</title>
      </Helmet>

      <SectionHeader _deprecate_marginTop={0} title="Queue Monitor" />
      <SectionContainer>
        {showError ? (
          <Paper
            sx={{
              padding: "20px",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
            }}
          >
            <Box sx={{ fontSize: "15px" }}>{errorMessage}</Box>
            <Button sx={{ marginTop: "15px" }} onClick={() => navigate("/")}>
              Go Back
            </Button>
          </Paper>
        ) : (
          <Paper variant="outlined">
            <QueueMonitorContextProvider queueMachineActor={queueMachineActor}>
              {queueMachineActor && <PollDataTable />}
              {hasMadeSelection && <PollWorkerDetailsDataTable />}
            </QueueMonitorContextProvider>
          </Paper>
        )}
      </SectionContainer>
    </>
  );
}

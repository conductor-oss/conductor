import React, { SyntheticEvent, useState } from "react";
import {
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Tab,
  Tabs,
  Typography,
} from "@mui/material";
import { useAction } from "utils/query";
import { maybeTriggerFailureWorkflow } from "utils/maybeTriggerWorkflow";
import {
  Button,
  DataTable,
  DropdownButton,
  Heading,
  LinearProgress,
} from "components";
import executionsStyles from "./executionsStyles";
import { useAuth } from "shared/auth";

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      {...other}
    >
      {value === index && (
        <Box sx={{ p: 3 }}>
          <Typography>{children}</Typography>
        </Box>
      )}
    </div>
  );
}

export default function BulkActionModule({
  selectedRows,
  refetchExecution,
  handleError,
}: {
  selectedRows: any[];
  refetchExecution: () => void;
  handleError: (error: any) => void;
}) {
  const { isTrialExpired } = useAuth();
  const selectedIds = selectedRows.map((row) => row.workflowId);
  const [results, setResults] = useState<any>(null);
  const [tab, setTab] = useState(0);

  const { mutate: pauseAction, isLoading: pauseLoading } = useAction(
    `/workflow/bulk/pause`,
    "put",
    { onSuccess, onError },
  );
  const { mutate: resumeAction, isLoading: resumeLoading } = useAction(
    `/workflow/bulk/resume`,
    "put",
    { onSuccess, onError },
  );
  const { mutate: restartCurrentAction, isLoading: restartCurrentLoading } =
    useAction(`/workflow/bulk/restart`, "post", { onSuccess });
  const { mutate: restartLatestAction, isLoading: restartLatestLoading } =
    useAction(`/workflow/bulk/restart?useLatestDefinitions=true`, "post", {
      onSuccess,
      onError,
    });
  const { mutate: retryAction, isLoading: retryLoading } = useAction(
    `/workflow/bulk/retry`,
    "post",
    { onSuccess, onError },
  );
  const { mutate: terminateAction, isLoading: terminateLoading } = useAction(
    `/workflow/bulk/terminate${maybeTriggerFailureWorkflow()}`,
    "post",
    { onSuccess, onError },
  );

  const isLoading =
    pauseLoading ||
    resumeLoading ||
    restartCurrentLoading ||
    restartLatestLoading ||
    retryLoading ||
    terminateLoading;

  function onSuccess(data: any) {
    const retval = {
      bulkErrorResults: Object.entries(data.bulkErrorResults).map(
        ([key, value]) => ({
          workflowId: key,
          message: value,
        }),
      ),
      bulkSuccessfulResults: data.bulkSuccessfulResults.map(
        (value: string) => ({
          workflowId: value,
        }),
      ),
    };
    setResults(retval);
  }

  function onError(error: any) {
    handleError(error);
  }

  function handleClose() {
    setResults(null);
    setTab(0);
    refetchExecution();
  }

  const handleTabChange = (_event: SyntheticEvent, newValue: number) => {
    setTab(newValue);
  };

  return (
    <Box style={executionsStyles.actionBar}>
      <Heading level={0}>{selectedRows.length} Workflows Selected.</Heading>
      {/*@ts-ignore*/}
      <DropdownButton
        buttonProps={{ disabled: isTrialExpired }}
        options={[
          {
            label: "Pause",
            // @ts-ignore
            handler: () => pauseAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Resume",
            // @ts-ignore
            handler: () => resumeAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Restart with current definitions",
            handler: () =>
              // @ts-ignore
              restartCurrentAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Restart with latest definitions",
            handler: () =>
              // @ts-ignore
              restartLatestAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Retry",
            // @ts-ignore
            handler: () => retryAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Terminate",
            handler: () =>
              // @ts-ignore
              terminateAction({ body: JSON.stringify(selectedIds) }),
          },
        ]}
      >
        Bulk Action
      </DropdownButton>
      {(results || isLoading) && (
        <Dialog
          open={true}
          fullScreen
          onClose={handleClose}
          style={{ padding: 30 }}
        >
          <DialogTitle>
            <Heading level={1}>Batch Actions</Heading>
          </DialogTitle>
          <DialogContent>
            {isLoading && <LinearProgress />}
            {results && (
              <Box sx={{ mt: 4 }}>
                <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
                  <Tabs value={tab} onChange={handleTabChange}>
                    <Tab
                      label={`Successful (${results.bulkSuccessfulResults.length})`}
                    />
                    <Tab
                      label={`Failed (${results.bulkErrorResults.length})`}
                      sx={{ color: "red" }}
                      disabled={results.bulkErrorResults.length === 0}
                    />
                  </Tabs>
                </Box>
                <TabPanel value={tab} index={0}>
                  <DataTable
                    title="Successful Operations"
                    columns={[
                      {
                        id: "workflowId",
                        name: "workflowId",
                        label: "Workflow Id",
                      },
                    ]}
                    data={results.bulkSuccessfulResults}
                    showColumnSelector={false}
                    hideSearch
                    pagination={results.bulkSuccessfulResults?.length > 15}
                  />
                </TabPanel>
                <TabPanel value={tab} index={1}>
                  <DataTable
                    title="Failed Operations"
                    columns={[
                      {
                        id: "workflowId",
                        name: "workflowId",
                        label: "Workflow Id",
                      },
                      {
                        id: "message",
                        name: "message",
                        label: "Message",
                        wrap: true,
                      },
                    ]}
                    data={results.bulkErrorResults}
                    showColumnSelector={false}
                    hideSearch
                    pagination={results.bulkErrorResults?.length > 15}
                  />
                </TabPanel>
              </Box>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Close</Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}

import React, { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogActions,
  DialogTitle,
} from "@material-ui/core";
import { makeStyles } from "@material-ui/styles";
import {
  DataTable,
  DropdownButton,
  LinearProgress,
  PrimaryButton,
  Heading,
} from "../../components";
import {
  useBulkRestartAction,
  useBulkRestartLatestAction,
  useBulkResumeAction,
  useBulkTerminateAction,
  useBulkPauseAction,
  useBulkRetryAction,
  useBulkTerminateWithReasonAction,
} from "../../data/bulkactions";

const useStyles = makeStyles({
  actionBar: {
    display: "flex",
    alignItems: "center",
    paddingRight: 10,
    "&>div, &>p": {
      marginRight: 10,
    },
    width: "100%",
    justifyContent: "space-between",
  },
});

export default function BulkActionModule({ selectedRows }) {
  const selectedIds = selectedRows.map((row) => row.workflowId);
  const [results, setResults] = useState();
  const classes = useStyles();

  const { mutate: pauseAction, isLoading: pauseLoading } = useBulkPauseAction({
    onSuccess,
  });
  const { mutate: resumeAction, isLoading: resumeLoading } =
    useBulkResumeAction({ onSuccess });
  const { mutate: restartCurrentAction, isLoading: restartCurrentLoading } =
    useBulkRestartAction({ onSuccess });
  const { mutate: restartLatestAction, isLoading: restartLatestLoading } =
    useBulkRestartLatestAction({ onSuccess });
  const { mutate: retryAction, isLoading: retryLoading } = useBulkRetryAction({
    onSuccess,
  });
  const { mutate: terminateAction, isLoading: terminateLoading } =
    useBulkTerminateAction({ onSuccess });
  const {
    mutate: terminateWithReasonAction,
    isLoading: terminateWithReasonLoading,
  } = useBulkTerminateWithReasonAction({ onSuccess });

  const isLoading =
    pauseLoading ||
    resumeLoading ||
    restartCurrentLoading ||
    restartLatestLoading ||
    retryLoading ||
    terminateLoading ||
    terminateWithReasonLoading;

  function onSuccess(data, variables, context) {
    const retval = {
      bulkErrorResults: Object.entries(data.bulkErrorResults).map(
        ([key, value]) => ({
          workflowId: key,
          message: value,
        })
      ),
      bulkSuccessfulResults: data.bulkSuccessfulResults.map((value) => ({
        workflowId: value,
      })),
    };
    setResults(retval);
  }

  function handleClose() {
    setResults(null);
  }

  return (
    <div className={classes.actionBar}>
      <Heading level={0}>{selectedRows.length} Workflows Selected.</Heading>
      <DropdownButton
        className={classes.actionButton}
        options={[
          {
            label: "Pause",
            handler: () => pauseAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Resume",
            handler: () => resumeAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Restart with current definitions",
            handler: () =>
              restartCurrentAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Restart with latest definitions",
            handler: () =>
              restartLatestAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Retry",
            handler: () => retryAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Terminate",
            handler: () =>
              terminateAction({ body: JSON.stringify(selectedIds) }),
          },
          {
            label: "Terminate with Reason",
            handler: () => {
              const reason = window.prompt("Termination Reason", "");
              if (reason) {
                terminateWithReasonAction({
                  body: JSON.stringify(selectedIds),
                  reason,
                });
              }
            },
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
            <Heading level={3} style={{ padding: 15 }}>
              Batch Actions
            </Heading>
            {isLoading && <LinearProgress />}
          </DialogTitle>
          <DialogContent>
            {results && (
              <React.Fragment>
                <DataTable
                  title="Successful Operations"
                  columns={[{ name: "workflowId" }]}
                  data={results.bulkSuccessfulResults}
                  pagination={false}
                  showColumnSelector={false}
                />
                <DataTable
                  title="Failed Operations"
                  columns={[
                    { name: "workflowId" },
                    { name: "message", wrap: true },
                  ]}
                  data={results.bulkErrorResults}
                  pagination={false}
                  showColumnSelector={false}
                />
              </React.Fragment>
            )}
          </DialogContent>
          <DialogActions>
            <PrimaryButton onClick={handleClose}>Close</PrimaryButton>
          </DialogActions>
        </Dialog>
      )}
    </div>
  );
}

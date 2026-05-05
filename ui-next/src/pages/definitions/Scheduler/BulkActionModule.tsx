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
import {
  Button,
  DataTable,
  DropdownButton,
  Heading,
  LinearProgress,
} from "components";

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const styles = {
  clickSearch: {
    width: "100%",
    padding: "30px",
    paddingBottom: "0px",
    display: "block",
    textAlign: "center",
  },
  paper: {
    marginBottom: "30px",
  },
  heading: {
    marginBottom: "20px",
    minHeight: "60px",
  },
  controls: {
    // padding: 15,
  },
  popupIndicator: {
    backgroundColor: "red",
  },
  banner: {
    marginBottom: "15px",
  },
  actionBar: {
    display: "flex",
    alignItems: "center",
    paddingRight: "10px",
    "&>div, &>p": {
      marginRight: "10px",
    },
    width: "100%",
    justifyContent: "space-between",
  },
};

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
  const selectedIds = selectedRows.map((row) => row.name);
  const [results, setResults] = useState<any>(null);
  const [tab, setTab] = useState(0);

  const { mutate: pauseAction, isLoading: pauseLoading } = useAction(
    `/scheduler/bulk/pause`,
    "put",
    { onSuccess, onError },
  );
  const { mutate: resumeAction, isLoading: resumeLoading } = useAction(
    `/scheduler/bulk/resume`,
    "put",
    { onSuccess, onError },
  );

  const isLoading = pauseLoading || resumeLoading;

  function onSuccess(data: any) {
    const retval = {
      bulkErrorResults: Object.entries(data.bulkErrorResults).map(
        ([key, value]) => ({
          name: key,
          message: value,
        }),
      ),
      bulkSuccessfulResults: data.bulkSuccessfulResults.map(
        (value: string) => ({
          name: value,
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
    <Box style={styles.actionBar}>
      <Heading level={0}>{selectedRows.length} Schedules Selected.</Heading>
      {/*@ts-ignore*/}
      <DropdownButton
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
                        id: "name",
                        name: "name",
                        label: "Schedule name",
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
                        id: "name",
                        name: "name",
                        label: "Schedule name",
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

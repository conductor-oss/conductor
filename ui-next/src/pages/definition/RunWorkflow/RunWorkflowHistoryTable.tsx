import { Box, Link as ClickableLink, Tooltip } from "@mui/material";
import IconButton from "@mui/material/IconButton";
import {
  Link as LinkIcon,
  ArrowCounterClockwise as Replay,
  Trash,
} from "@phosphor-icons/react";
import { Button, DataTable, Paper } from "components";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import _difference from "lodash/difference";
import _isEmpty from "lodash/isEmpty";
import _isEqual from "lodash/isEqual";
import { FunctionComponent, useMemo, useState } from "react";
import { ExtendedFieldsData, FieldsData, RunWorkflowParamType } from "./state";

interface RunWorkflowHistoryTableProps {
  workflowName?: string;
  fillReRunWfFields: (data: RunWorkflowParamType) => void;
  workflowHistory: ExtendedFieldsData[];
  setWorkflowHistory: (data: FieldsData[]) => void;
}

export const RunWorkflowHistoryTable: FunctionComponent<
  RunWorkflowHistoryTableProps
> = ({
  workflowName,
  fillReRunWfFields,
  workflowHistory,
  setWorkflowHistory,
}) => {
  const filteredWorkflowHistory = useMemo(() => {
    let newHistory = [];
    if (workflowName && Array.isArray(workflowHistory)) {
      newHistory = workflowHistory.filter((item) => item.name === workflowName);
      return newHistory;
    }
    return workflowHistory;
  }, [workflowName, workflowHistory]);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const showExecution = (executionlink: string) => {
    if (Array.isArray(workflowHistory)) {
      const found = workflowHistory.find(
        (el) => el.executionLink === executionlink,
      );
      if (!found) {
        return;
      }
      window.open(`/execution/${executionlink}`, "_blank", "noreferrer");
    }
  };

  const handleClearWorkflowHistory = () => {
    let remainingHistory: FieldsData[] = [];
    if (_isEqual(workflowHistory, filteredWorkflowHistory)) {
      setWorkflowHistory([]);
    } else {
      remainingHistory = _difference(workflowHistory, filteredWorkflowHistory);
      setWorkflowHistory(remainingHistory);
    }
  };

  return (
    <Paper
      id="run-workflow-history-table-wrapper"
      variant="outlined"
      sx={{ width: "100%" }}
    >
      {showConfirmDialog && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(confirmed) => {
            if (confirmed) {
              handleClearWorkflowHistory();
              setShowConfirmDialog(false);
            } else {
              setShowConfirmDialog(false);
            }
          }}
          message={"Are you sure you want to delete the Run Workflow History?"}
        />
      )}
      <DataTable
        title="Workflow run history"
        defaultShowColumns={[
          "name",
          "executionLink",
          "executionTime",
          "useData",
        ]}
        pagination={false}
        noDataComponent={
          <Box padding={5} fontWeight={600}>
            History is empty
          </Box>
        }
        defaultSortFieldId="executionTime"
        defaultSortAsc={false}
        columns={[
          {
            id: "name",
            name: "name",
            label: "Execution name",
            grow: 0.5,
            renderer: (val, row) => {
              return (
                <div>
                  {row.executionLink ? (
                    <ClickableLink
                      sx={{ cursor: "pointer" }}
                      onClick={() => showExecution(row.executionLink)}
                      target="noreferrer noopener"
                    >
                      {row.name}
                    </ClickableLink>
                  ) : (
                    row.name
                  )}
                </div>
              );
            },
          },
          {
            id: "executionLink",
            name: "executionLink",
            label: "Execution link",
            grow: 0,
            center: true,
            renderer: (val) => {
              return (
                <div>
                  {val && (
                    <ClickableLink
                      sx={{ cursor: "pointer" }}
                      onClick={() => showExecution(val)}
                      //target="noreferrer noopener"
                      // path={`/execution/${val}`}
                    >
                      <LinkIcon size={20}></LinkIcon>
                    </ClickableLink>
                  )}
                </div>
              );
            },
          },
          {
            id: "executionTime",
            name: "executionTime",
            label: "Execution time",
            grow: 0.25,
            renderer: (val) => {
              return new Date(val).toLocaleString();
            },
          },
          {
            id: "useData",
            name: "useData",
            label: "Restore form values",
            grow: 0.25,
            right: true,
            renderer: (val, row) => {
              return (
                <IconButton
                  size="small"
                  onClick={() => {
                    fillReRunWfFields(row);
                  }}
                >
                  <Replay />
                </IconButton>
              );
            },
          },
        ]}
        data={filteredWorkflowHistory}
        actions={[
          <Tooltip title="Reset all histories" key="reset">
            <Button
              size="small"
              color="tertiary"
              startIcon={<Trash size={20} />}
              disabled={_isEmpty(filteredWorkflowHistory)}
              onClick={() => {
                setShowConfirmDialog(true);
              }}
            >
              Reset
            </Button>
          </Tooltip>,
        ]}
      />
    </Paper>
  );
};

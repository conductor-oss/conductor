import {
  Box,
  FormControl,
  FormControlLabel,
  Grid,
  Radio,
  RadioGroup,
  Tooltip,
} from "@mui/material";
import {
  CopySimple as CopyIcon,
  Trash as DeleteIcon,
  PauseCircle as PauseIcon,
  ArrowClockwise as RefreshIcon,
  Tag as TagIcon,
} from "@phosphor-icons/react";
import { Button, DataTable, IconButton, NavLink } from "components";
import { ColumnCustomType } from "components/ui/DataTable/types";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import Paper from "components/ui/Paper";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import TagChip from "components/ui/TagChip";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import AddTagDialog from "components/features/tags/AddTagDialog";
import ConductorInput from "components/ui/inputs/ConductorInput";
import TagList from "components/ui/TagList";
import AddIcon from "components/icons/AddIcon";
import PlayIcon from "components/icons/PlayIcon";
import cronstrue from "cronstrue";
import { useSaveSchedule } from "pages/scheduler/schedulerHooks";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeader from "components/layout/SectionHeader";
import SectionHeaderActions from "components/ui/layout/SectionHeaderActions";
import { useAuth } from "components/features/auth";
import { colors } from "theme/tokens/variables";
import { PopoverMessage } from "types/Messages";
import { IScheduleDto, IStartWorkflowRequest } from "types/Schedulers";
import { TagDto } from "types/Tag";
import { HTTPMethods } from "types/TaskType";
import { getSequentiallySuffix, logger } from "utils";
import {
  ACTIVE_FILTER_QUERY_PARAM,
  generateForbiddenMessage,
} from "utils/constants/common";
import { SCHEDULER_DEFINITION_URL } from "utils/constants/route";
import {
  useGetSchedulerDefinitions,
  useGetSchedulerDefinitionsWithPagination,
  SchedulerSearchParams,
} from "utils/hooks/useGetSchedulerDefinitions";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useActionWithPath } from "utils/query";
import { createSearchableTags } from "utils/utils";
import CloneScheduleDialog from "../dialog/CloneScheduleDialog";
import {
  activeFilterGroups,
  activeLinkColor,
  conditionalRowStyles,
  getLinkColor,
  pausedLinkColor,
  pausedrowColor,
} from "../rowColorHelpers";
import BulkActionModule from "./BulkActionModule";

const INTRO_CONTENT = `Schedulers help you automate workflow execution using cron expressions. Set up recurring workflows with precise timing control, perfect for batch processing, periodic data syncs, or any time-based automation needs.

Read more:
* [Developer Guides: Scheduling Workflows](https://orkes.io/content/developer-guides/scheduling-workflows)
* [Schedule API Reference](https://orkes.io/content/reference-docs/api/schedule)
`;

const getNameAndVersion = (workflow: IStartWorkflowRequest | undefined) => {
  if (!workflow) {
    return "Undefined Workflow";
  }
  return workflow.version !== undefined
    ? `${workflow.name} - Version: ${workflow.version}`
    : `${workflow.name} - Latest`;
};

const customSortForWorkflowColumn = (
  rowA: IScheduleDto,
  rowB: IScheduleDto,
) => {
  const nameWithVersionA = getNameAndVersion(rowA.startWorkflowRequest);
  const nameWithVersionB = getNameAndVersion(rowB.startWorkflowRequest);
  return nameWithVersionA
    .toLowerCase()
    .localeCompare(nameWithVersionB.toLowerCase());
};

const searchableWorkflow = (workflow: IStartWorkflowRequest) => {
  return workflow.version !== undefined
    ? `${workflow.name} - Version: ${workflow.version}`
    : `${workflow.name} - Latest`;
};

const columns = [
  {
    id: "cronExpression",
    name: "cronExpression",
    label: "Cron expression",
    renderer: (cron: string) => {
      if (!cron) {
        return "";
      }
      return (
        <Tooltip title={cron}>
          <span>{cron ? cronstrue.toString(cron) : ""}</span>
        </Tooltip>
      );
    },
    tooltip: "Cron expression",
    sortable: false,
  },
  {
    id: "name",
    name: "name",
    label: "Schedule name",
    sortable: true,
    renderer: (val: string, row: IScheduleDto) => (
      <NavLink
        style={{
          color: row.active ? `${activeLinkColor}` : `${pausedLinkColor}`,
        }}
        path={`${SCHEDULER_DEFINITION_URL.BASE}/${val.trim()}`}
      >
        {val.trim()}
      </NavLink>
    ),
    grow: 1.3,
    tooltip: "The name of the schedule",
  },
  {
    id: "nextRunTime",
    name: "nextRunTime",
    label: "Next run time",
    type: ColumnCustomType.DATE,
    sortable: false,
    grow: 1,
    tooltip: "The next time the schedule will run",
  },
  {
    id: "tags",
    name: "tags",
    label: "Tags",
    searchable: true,
    sortable: false,
    searchableFunc: (tags: TagDto[]) => createSearchableTags(tags),
    renderer: (tags: TagDto[], row: IScheduleDto) => (
      <TagList
        tags={tags}
        name={row?.name}
        style={{ color: row.active ? "black" : pausedrowColor }}
      />
    ),
    grow: 1,
    tooltip: "Tags associated with the schedule",
  },
  {
    id: "startWorkflowRequest",
    name: "startWorkflowRequest",
    label: "Workflow",
    sortable: true,
    grow: 1.5,
    searchableFunc: (workflow: IStartWorkflowRequest) =>
      searchableWorkflow(workflow),
    renderer: (val: IStartWorkflowRequest) => {
      if (val.version !== undefined) {
        return `${val.name} - Version: ${val.version}`;
      } else {
        return `${val.name} - Latest`;
      }
    },
    sortFunction: customSortForWorkflowColumn,
    tooltip: "The workflow associated with the schedule",
  },
  {
    id: "createTime",
    name: "createTime",
    label: "Created time",
    type: ColumnCustomType.DATE,
    sortable: true,
    tooltip: "The time the schedule was created",
  },
  {
    id: "lastRunTimeInEpoch",
    name: "lastRunTimeInEpoch",
    label: "Last Run time",
    type: ColumnCustomType.DATE,
    sortable: false,
    tooltip: "The last time the schedule ran",
  },
  {
    id: "createdBy",
    name: "createdBy",
    label: "Created by",
    grow: 1,
    sortable: false,
    tooltip: "The user who created the schedule",
  },
  {
    id: "updatedBy",
    name: "updatedBy",
    label: "Updated by",
    grow: 1,
    sortable: false,
    tooltip: "The user who last updated the schedule",
  },
  {
    id: "paused",
    name: "active",
    label: "Status",
    grow: 0.5,
    minWidth: "120px",
    tooltip: "The status of the schedule",
    renderer: (val: boolean) => {
      return (
        <Box>
          <TagChip
            style={{
              background: val ? colors.successTag : colors.errorTag,
              padding: "0 12px",
              fontSize: "10px",
              fontWeight: 500,
            }}
            label={val ? "Active" : "Inactive"}
          />
        </Box>
      );
    },
  },
  {
    id: "workflowExecutionsLink",
    name: "name",
    selector: (row: IScheduleDto) => row.name,
    label: "Workflow executions",
    searchable: false,
    grow: 1,
    sortable: false,
    tooltip: "The workflow executions associated with the schedule",
    renderer: (name: string, rec: IScheduleDto) => (
      <NavLink
        style={{
          color: getLinkColor(rec),
        }}
        path={`/executions?freeText=${rec.name}&workflowType=${rec?.startWorkflowRequest?.name}`}
      >
        Workflow query
      </NavLink>
    ),
  },
  {
    id: "schedulerExecutionsLink",
    name: "name",
    selector: (row: IScheduleDto) => row.name,
    label: "Scheduler executions",
    searchable: false,
    sortable: false,
    grow: 1,
    tooltip: "The scheduler executions associated with the schedule",
    renderer: (name: string, rec: IScheduleDto) => (
      <NavLink
        style={{
          color: getLinkColor(rec),
        }}
        path={`/schedulerExecs?scheduleName=${name}`}
      >
        Scheduler query
      </NavLink>
    ),
  },
];

export default function ScheduleDefinitions() {
  const [selectedRows, setSelectedRows] = useState<string[]>([]);
  const [toggleCleared, setToggleCleared] = useState(false);

  // Pagination state
  const [page, setPage] = useState(1);
  const [rowsPerPage, setRowsPerPage] = useState(15);
  const [sort, setSort] = useState<string | undefined>(undefined);
  const [searchTerm, setSearchTerm] = useState("");

  const { isTrialExpired } = useAuth();
  const [toast, setToast] = useState({
    isOpen: false,
    message: "",
    status: "",
  });

  const initialState = {
    confirmationDialogDeleteOpen: false,
    scheduleName: "",
  };
  const [deleteScheduleState, setDeleteScheduleState] = useState(initialState);
  const [errorMessage, setErrorMessage] = useState("");
  const [selectedSchedule, setSelectedSchedule] = useState<IScheduleDto | null>(
    null,
  );
  const [toastMessage, setToastMessage] = useState<PopoverMessage | null>(null);

  const [activeFilterParam, setActiveFilterParam] = useQueryState(
    ACTIVE_FILTER_QUERY_PARAM,
    "all",
  );

  // Build search params for pagination
  const searchParams: SchedulerSearchParams = useMemo(() => {
    const params: SchedulerSearchParams = {
      start: (page - 1) * rowsPerPage,
      size: rowsPerPage,
    };

    if (sort) {
      params.sort = sort;
    }

    if (searchTerm) {
      params.name = searchTerm;
    }

    // Map active filter to paused parameter
    if (activeFilterParam === "yes") {
      params.paused = false; // Active schedules (not paused)
    } else if (activeFilterParam === "no") {
      params.paused = true; // Inactive schedules (paused)
    }
    // If "all", don't set paused parameter

    return params;
  }, [page, rowsPerPage, sort, searchTerm, activeFilterParam]);

  const {
    data: paginatedData,
    isFetching,
    refetch,
  } = useGetSchedulerDefinitionsWithPagination(searchParams);

  // For backward compatibility with clone dialog (fetch all schedule names)
  const { data: allSchedulesData } = useGetSchedulerDefinitions();

  useEffect(() => {
    setSelectedRows([]);
    setToggleCleared((t) => !t);
  }, [paginatedData]);

  const handleFetchError = async (error: Response, method: HTTPMethods) => {
    logger.error("[Schedules.tsx][handleFetchError] Error:", error);

    if (error.status >= 400) {
      switch (error.status) {
        case 403:
          setErrorMessage(generateForbiddenMessage(method));
          break;
        default: {
          // Check if the response is JSON
          const isJSON = error.headers
            .get("content-type")
            ?.includes("application/json");
          const response = isJSON ? await error.json() : await error.text();

          setErrorMessage(isJSON ? response?.message : response);
        }
      }
    }

    refetch();
  };

  const pushHistory = usePushHistory();

  const { mutate: saveSchedule, isLoading: isSavingSchedule } = useSaveSchedule(
    {
      onSuccess: () => {
        refetch();
        setSelectedSchedule(null);
        setToastMessage({
          text: "Schedule cloned successfully",
          severity: "success",
        });
      },

      onError: (error: Response) => handleFetchError(error, HTTPMethods.POST),
    },
  );

  const deleteScheduleAction = useActionWithPath({
    onSuccess: () => {
      refetch();
    },
    onError: (error: Response) => handleFetchError(error, HTTPMethods.DELETE),
  });

  const [addTagDialogData, setAddTagDialogData] = useState<IScheduleDto | null>(
    null,
  );
  const [showAddTagDialog, setShowAddTagDialog] = useState(false);

  // Transform paginated data to add 'active' field
  const schedules = useMemo(() => {
    if (paginatedData?.results) {
      return paginatedData.results.map((schedule) => ({
        ...schedule,
        active: !schedule.paused,
      }));
    }
    return [];
  }, [paginatedData]);

  const scheduleNames: string[] = useMemo(
    () =>
      allSchedulesData
        ? allSchedulesData.map((schedule: IScheduleDto) => schedule.name)
        : [],
    [allSchedulesData],
  );

  const totalCount = paginatedData?.totalHits ?? 0;

  const pauseScheduleAction = useActionWithPath({
    onSuccess: () => {
      refetch();
    },
    onError: (error: Response) => handleFetchError(error, HTTPMethods.GET),
  });

  const handlePauseSchedule = useCallback(
    (scheduleName: string) => {
      if (scheduleName) {
        // @ts-ignore
        pauseScheduleAction.mutate({
          method: "get",
          path: `/scheduler/schedules/${scheduleName}/pause`,
        });
        setToast({
          isOpen: true,
          message: `${scheduleName} is now paused.`,
          status: "paused",
        });
      }
    },
    [pauseScheduleAction],
  );

  const handleResumeSchedule = useCallback(
    (scheduleName: string) => {
      if (scheduleName) {
        // @ts-ignore
        pauseScheduleAction.mutate({
          method: "get",
          path: `/scheduler/schedules/${scheduleName}/resume`,
        });
        setToast({
          isOpen: true,
          message: `${scheduleName} is now running.`,
          status: "running",
        });
      }
    },
    [pauseScheduleAction],
  );

  const deleteSchedule = (name: string) => {
    if (name && name !== "") {
      setDeleteScheduleState((prevState) => ({
        ...prevState,
        confirmationDialogDeleteOpen: true,
        scheduleName: name,
      }));
    } else {
      logger.log(
        "No schedule selected for deletion. Unable to recognize name from the definition.",
      );
    }
  };

  const handleDeleteScheduleConfirmation = (val: boolean) => {
    setDeleteScheduleState(initialState);
    if (val) {
      // @ts-ignore
      deleteScheduleAction.mutate({
        method: "delete",
        path: `/scheduler/schedules/${deleteScheduleState.scheduleName}`,
      });
    }
  };

  const renderColumns = useMemo(
    () => [
      ...columns,
      {
        id: "actions",
        name: "name",
        selector: (row: IScheduleDto) => row.name,
        label: "Actions",
        right: true,
        sortable: false,
        searchable: false,
        grow: 0.5,
        minWidth: "160px",
        renderer: (name: string, row: IScheduleDto) => (
          <Box style={{ display: "flex", justifyContent: "space-evenly" }}>
            {row.active && (
              <Tooltip title={"Pause schedule"}>
                <IconButton
                  onClick={() => handlePauseSchedule(name)}
                  color="primary"
                  disabled={isTrialExpired}
                >
                  <PauseIcon size={22} />
                </IconButton>
              </Tooltip>
            )}

            {!row.active && (
              <Tooltip title={"Resume schedule"}>
                <IconButton
                  onClick={() => handleResumeSchedule(name)}
                  color="primary"
                  disabled={isTrialExpired}
                >
                  <PlayIcon size={22} />
                </IconButton>
              </Tooltip>
            )}

            <Tooltip title={"Clone schedule"}>
              <IconButton
                onClick={() => setSelectedSchedule(row)}
                size="small"
                disabled={isTrialExpired}
                sx={{
                  whiteSpace: "nowrap",
                }}
              >
                <CopyIcon size={20} />
              </IconButton>
            </Tooltip>

            <Tooltip title={"Add/Edit tags"}>
              <IconButton
                disabled={isTrialExpired}
                onClick={() => {
                  setAddTagDialogData(row);
                  setShowAddTagDialog(true);
                }}
                size="small"
              >
                <TagIcon />
              </IconButton>
            </Tooltip>

            <Tooltip title={"Delete schedule"}>
              <IconButton
                disabled={isTrialExpired}
                onClick={() => deleteSchedule(name)}
              >
                <DeleteIcon size={20} />
              </IconButton>
            </Tooltip>
          </Box>
        ),
      },
    ],
    [handlePauseSchedule, handleResumeSchedule, isTrialExpired],
  );

  const handleClickDefineSchedule = () => {
    pushHistory(SCHEDULER_DEFINITION_URL.NEW);
  };

  const handleError = (error: any) => {
    setErrorMessage(error?.message);
  };

  // Pagination handlers
  const handlePageChange = (newPage: number) => {
    setPage(newPage);
  };

  const handleRowsPerPageChange = (newRowsPerPage: number) => {
    setRowsPerPage(newRowsPerPage);
    setPage(1); // Reset to first page when changing rows per page
  };

  const handleSort = (column: any, sortDirection: string) => {
    if (column.id) {
      // Format: "fieldName:ASC" or "fieldName:DESC"
      const sortParam = `${column.id}:${sortDirection.toUpperCase()}`;
      setSort(sortParam);
      setPage(1); // Reset to first page when sorting
    }
  };

  const handleSearchTermChange = (value: string) => {
    setSearchTerm(value);
    setPage(1); // Reset to first page when searching
  };

  // Reset page when active filter changes
  useEffect(() => {
    setPage(1);
  }, [activeFilterParam]);

  return (
    <>
      <Helmet>
        <title>Workflow Scheduler Definitions</title>
      </Helmet>
      {errorMessage && (
        <SnackbarMessage
          message={errorMessage}
          severity="error"
          onDismiss={() => setErrorMessage("")}
        />
      )}
      {toast.isOpen && (
        <SnackbarMessage
          autoHideDuration={3000}
          message={toast.message}
          severity={toast.status === "paused" ? "warning" : "success"}
          onDismiss={() => setToast({ isOpen: false, message: "", status: "" })}
          anchorOrigin={{
            vertical: "bottom",
            horizontal: "right",
          }}
        />
      )}
      {deleteScheduleState.confirmationDialogDeleteOpen && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(val) =>
            handleDeleteScheduleConfirmation(val)
          }
          message={
            <>
              Are you sure you want to delete{" "}
              <strong style={{ color: "red" }}>
                {deleteScheduleState.scheduleName}
              </strong>{" "}
              schedule definition? This action cannot be undone.
              <div style={{ marginTop: "15px" }}>
                Please type <strong>{deleteScheduleState.scheduleName}</strong>{" "}
                to confirm.
              </div>
            </>
          }
          header={"Deletion confirmation"}
          isInputConfirmation
          valueToBeDeleted={deleteScheduleState.scheduleName}
        />
      )}

      <AddTagDialog
        open={showAddTagDialog && !!addTagDialogData}
        tags={addTagDialogData?.tags || []}
        itemName={addTagDialogData?.name}
        onClose={() => {
          setShowAddTagDialog(false);
        }}
        onSuccess={() => {
          setShowAddTagDialog(false);
          refetch();
        }}
        apiPath={`/scheduler/schedules/${addTagDialogData?.name}/tags`}
      />

      {selectedSchedule && (
        <CloneScheduleDialog
          name={
            getSequentiallySuffix({
              name: selectedSchedule.name,
              refNames: scheduleNames,
            }).name
          }
          onClose={() => setSelectedSchedule(null)}
          onSuccess={({ name }) => {
            // @ts-ignore
            saveSchedule({
              body: JSON.stringify({ ...selectedSchedule, name }),
            });
          }}
          scheduleNames={scheduleNames}
          isFetching={isSavingSchedule}
        />
      )}
      <SectionHeader
        title="Workflow Scheduler Definitions"
        _deprecate_marginTop={0}
        actions={
          <SectionHeaderActions
            buttons={[
              {
                label: "Define schedule",
                onClick: () => pushHistory(SCHEDULER_DEFINITION_URL.NEW),
                startIcon: <AddIcon />,
              },
            ]}
          />
        }
      />
      <SectionContainer>
        {/*@ts-ignore*/}
        <Paper variant="outlined">
          <Box padding={6}>
            <Grid container sx={{ width: "100%" }} spacing={3}>
              <Grid
                size={{
                  xs: 12,
                  md: 4,
                }}
              >
                <ConductorInput
                  fullWidth
                  label="Quick search"
                  placeholder="Search scheduler definitions"
                  showClearButton
                  value={searchTerm}
                  onTextInputChange={handleSearchTermChange}
                  autoFocus
                />
              </Grid>
              <Grid
                size={{
                  xs: 12,
                  md: 0.1,
                }}
              ></Grid>
              <Grid
                size={{
                  xs: 12,
                  md: 7,
                }}
              >
                <Box sx={{ width: "100%" }}>
                  <label>Quick filters</label>
                  <FormControl id="fmpaused">
                    <FormControlLabel
                      labelPlacement="start"
                      control={
                        <>
                          <RadioGroup
                            row
                            aria-labelledby="ActiveFilterField"
                            name="activeRadioGroup"
                            value={activeFilterParam}
                            onChange={(e) =>
                              setActiveFilterParam(e.target.value)
                            }
                            sx={{ marginLeft: 2 }}
                          >
                            {activeFilterGroups.map((item, index) => (
                              <FormControlLabel
                                key={index}
                                value={item.value}
                                control={<Radio />}
                                label={item.title}
                              />
                            ))}
                          </RadioGroup>
                        </>
                      }
                      label="Status:"
                      sx={{
                        marginLeft: 0,
                        "& .MuiFormControlLabel-label": {
                          fontWeight: 100,
                          color: "gray",
                        },
                      }}
                    />
                  </FormControl>
                </Box>
              </Grid>
            </Grid>
          </Box>
          <Header loading={isFetching} />
          {schedules != null && paginatedData != null && (
            <Box sx={{ maxWidth: "100%", overflowX: "scroll" }}>
              <DataTable
                title={`${schedules.length} results of ${totalCount}`}
                localStorageKey="schedulesTable"
                conditionalRowStyles={conditionalRowStyles}
                defaultShowColumns={[
                  "name",
                  "nextRunTime",
                  "workflowExecutionsLink",
                  "schedulerExecutionsLink",
                  "tags",
                  "cronTabExpression",
                  "startWorkflowRequest",
                  "createTime",
                  "paused",
                  "actions",
                ]}
                keyField="name"
                hideSearch
                sortByDefault={false}
                data={schedules}
                columns={renderColumns}
                customActions={[
                  <Tooltip
                    title="Refresh scheduler definitions"
                    key={"refToolTip"}
                  >
                    <Button
                      variant="text"
                      size="small"
                      startIcon={<RefreshIcon />}
                      key="refresh"
                      onClick={() => refetch()}
                      sx={{
                        color: (theme) =>
                          theme.palette.mode === "dark"
                            ? colors.gray13
                            : colors.gray02,
                      }}
                    >
                      Refresh
                    </Button>
                  </Tooltip>,
                ]}
                pagination
                paginationServer
                paginationTotalRows={totalCount}
                paginationDefaultPage={page}
                paginationPerPage={rowsPerPage}
                onChangePage={handlePageChange}
                onChangeRowsPerPage={handleRowsPerPageChange}
                sortServer
                defaultSortFieldId={sort ? undefined : "createTime"}
                defaultSortAsc={false}
                onSort={handleSort}
                selectableRows
                contextComponent={
                  <BulkActionModule
                    selectedRows={selectedRows}
                    refetchExecution={refetch}
                    handleError={handleError}
                  />
                }
                onSelectedRowsChange={({ selectedRows }) =>
                  setSelectedRows(selectedRows)
                }
                clearSelectedRows={toggleCleared}
                customStyles={{
                  header: {
                    style: {
                      overflow: "visible",
                    },
                  },
                  contextMenu: {
                    style: {
                      display: "none",
                    },
                    activeStyle: {
                      display: "flex",
                    },
                  },
                }}
                noDataComponent={
                  <NoDataComponent
                    title="Scheduler"
                    description={INTRO_CONTENT}
                    buttonText="Define a Schedule"
                    buttonHandler={handleClickDefineSchedule}
                    disableButton={isTrialExpired}
                  />
                }
              />
            </Box>
          )}
        </Paper>
      </SectionContainer>
      {toastMessage && (
        <SnackbarMessage
          autoHideDuration={3000}
          id="schedules-page-toast-message"
          message={toastMessage.text}
          severity={toastMessage.severity}
          onDismiss={() => {
            setToastMessage(null);
          }}
        />
      )}
    </>
  );
}

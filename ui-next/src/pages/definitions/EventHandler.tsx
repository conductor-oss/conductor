import {
  FormControl,
  FormControlLabel,
  Grid,
  Radio,
  RadioGroup,
  Tooltip,
} from "@mui/material";
import { Box } from "@mui/system";
import {
  Trash as DeleteIcon,
  PauseCircle as PauseIcon,
  ArrowClockwise as RefreshIcon,
  TagIcon,
} from "@phosphor-icons/react";
import { Button, DataTable, IconButton, NavLink } from "components";
import NoDataComponent from "components/NoDataComponent";
import Paper from "components/Paper";
import { SnackbarMessage } from "components/SnackbarMessage";
import TagChip from "components/TagChip";
import ConfirmChoiceDialog from "components/ConfirmChoiceDialog";
import AddTagDialog, { TagDialogProps } from "components/tags/AddTagDialog";
import ConductorInput from "components/v1/ConductorInput";
import { TagsRenderer } from "components/v1/TagList";
import AddIcon from "components/v1/icons/AddIcon";
import PlayIcon from "components/v1/icons/PlayIcon";
import { useCallback, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "shared/SectionContainer";
import SectionHeader from "shared/SectionHeader";
import SectionHeaderActions from "shared/SectionHeaderActions";
import { useAuth } from "shared/auth";
import { colors } from "theme/tokens/variables";
import { ConductorEvent } from "types/Events";
import { TagDto } from "types/Tag";
import { createSearchableTags, logger } from "utils";
import { ACTIVE_FILTER_QUERY_PARAM } from "utils/constants/common";
import { EVENT_HANDLERS_URL } from "utils/constants/route";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { useActionWithPath, useFetch } from "utils/query";
import Header from "../../components/Header";
import {
  activeFilterGroups,
  conditionalRowStyles,
  getLinkColor,
} from "./rowColorHelpers";

const getStatusLabel = (status: boolean) => (status ? "Active" : "Inactive");

const INTRO_CONTENT = `Event handlers help you automate workflow responses to external events. Create handlers to trigger workflows when events occur from sources like Kafka, SQS, or custom events. Perfect for building event-driven architectures and real-time integrations.

Read more:

* [Developer Guides: Event Handlers](https://orkes.io/content/developer-guides/event-handler)
* [Eventing](https://orkes.io/content/eventing)
`;

export default function EventDefinitionList() {
  const { data: eventHandlers = [], isFetching, refetch } = useFetch("/event");
  const { isTrialExpired } = useAuth();
  const [toast, setToast] = useState({
    isOpen: false,
    message: "",
    status: "",
  });
  const [showAddTagDialog, setShowAddTagDialog] = useState(false);
  const [addTagDialogData, setAddTagDialogData] =
    useState<TagDialogProps | null>(null);

  const pushHistory = usePushHistory();
  const [
    { pageParam, searchParam },
    { handlePageChange, handleSearchTermChange },
  ] = useCustomPagination();

  const pauseActiveEventAction = useActionWithPath({
    onSuccess: () => {
      refetch();
    },
    onError: (error: Response) => {
      logger.error(error);
      refetch();
    },
  });

  const [activeFilterParam, setActiveFilterParam] = useQueryState(
    ACTIVE_FILTER_QUERY_PARAM,
    "all",
  );

  const activeNonActiveFiltered = useMemo(
    () =>
      eventHandlers.filter(
        ({ active }: ConductorEvent) =>
          activeFilterParam === "all" ||
          (activeFilterParam === "yes" && active) ||
          (activeFilterParam === "no" && !active),
      ),
    [eventHandlers, activeFilterParam],
  );

  const handlePauseResumeEvent = useCallback(
    (event: ConductorEvent, active: boolean) => {
      if (event) {
        // @ts-ignore
        pauseActiveEventAction.mutate({
          method: "put",
          path: `/event`,
          body: JSON.stringify({
            ...event,
            active,
          }),
        });
        setToast({
          isOpen: true,
          message: `${event.name} is now ${active ? "running" : "paused"}.`,
          status: `${active ? "running" : "paused"}`,
        });
      }
    },
    [pauseActiveEventAction],
  );

  const [confirmDeleteName, setConfirmDeleteName] = useState("");

  const columns = [
    {
      id: "name",
      name: "name",
      label: "Event handler name",
      renderer: (name: string, rec: ConductorEvent) => (
        <NavLink
          style={{ color: getLinkColor(rec) }}
          path={`${EVENT_HANDLERS_URL.BASE}/${name}`}
        >
          {name}
        </NavLink>
      ),
    },
    { id: "event", name: "event", label: "Event" },
    {
      id: "event_tags",
      name: "tags",
      label: "Tags",
      searchable: true,
      searchableFunc: (tags: TagDto[]) => createSearchableTags(tags),
      renderer: TagsRenderer,
      grow: 2,
      tooltip: "The tags associated with the event handler",
    },
    {
      id: "active",
      name: "active",
      label: "Status",
      searchable: true,
      searchableFunc: getStatusLabel,
      renderer(status: boolean) {
        return (
          <Box>
            <TagChip
              style={{
                background: status ? colors.successTag : colors.errorTag,
                padding: "0 12px",
                fontSize: "10px",
                fontWeight: 500,
              }}
              label={getStatusLabel(status)}
            />
          </Box>
        );
      },
    },
    {
      id: "actions",
      name: "actions",
      label: "Actions",
      sortable: false,
      searchable: false,
      grow: 0.5,
      right: true,
      renderer: (__: string, taskRowData: any) => (
        <Box sx={{ display: "flex", justifyContent: "space-evenly", gap: 2 }}>
          {taskRowData.active && (
            <Tooltip title={"Pause event"}>
              <IconButton
                onClick={() => handlePauseResumeEvent(taskRowData, false)}
                color="primary"
                disabled={isTrialExpired}
                size="small"
              >
                <PauseIcon size={22} />
              </IconButton>
            </Tooltip>
          )}
          <Tooltip title={"Add/Edit tags"}>
            <IconButton
              id={`add-tags-${taskRowData.name}-btn`}
              disabled={isTrialExpired}
              onClick={() => {
                setAddTagDialogData({
                  tags: taskRowData.tags || [],
                  itemName: taskRowData.name,
                  itemType: "event",
                } as TagDialogProps);
                setShowAddTagDialog(true);
              }}
              size="small"
            >
              <TagIcon size={20} />
            </IconButton>
          </Tooltip>
          {!taskRowData.active && (
            <Tooltip title={"Resume event"}>
              <IconButton
                onClick={() => handlePauseResumeEvent(taskRowData, true)}
                color="primary"
                size="small"
                disabled={isTrialExpired}
              >
                <PlayIcon size={22} />
              </IconButton>
            </Tooltip>
          )}
          <Tooltip title={"Delete event handler"}>
            <IconButton
              id={`delete-${taskRowData.name}-btn`}
              onClick={() => {
                setConfirmDeleteName(taskRowData?.name);
              }}
              disabled={isTrialExpired}
              size="small"
              sx={{
                whiteSpace: "nowrap",
              }}
            >
              <DeleteIcon size={20} />
            </IconButton>
          </Tooltip>
        </Box>
      ),
    },
  ];

  const deleteEventHandler = useActionWithPath({
    onSuccess: () => {
      refetch();
    },
    onError: (err: Error) => {
      logger.error(err);
      refetch();
    },
  });

  const handleClickDefineEventHandler = () => {
    pushHistory(EVENT_HANDLERS_URL.NEW);
  };

  return (
    <Box id="event-handler-list">
      <Helmet>
        <title>Event Handler Definitions</title>
      </Helmet>
      {confirmDeleteName && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(selectedChoice) => {
            if (selectedChoice) {
              // @ts-ignore
              deleteEventHandler.mutate({
                method: "delete",
                path: encodeURI(`/event/${confirmDeleteName}`),
              });
            }
            setConfirmDeleteName("");
          }}
          message={
            <>
              <>
                Are you sure you want to delete{" "}
                <strong style={{ color: "red" }}>{confirmDeleteName}</strong>{" "}
                Event Handler definition? This change cannot be undone.
                <div style={{ marginTop: "15px" }}>
                  Please type <strong>{confirmDeleteName}</strong> to confirm
                </div>
              </>
            </>
          }
          header="Delete event handler"
          valueToBeDeleted={confirmDeleteName}
          isInputConfirmation
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

      <AddTagDialog
        open={showAddTagDialog && !!addTagDialogData}
        tags={addTagDialogData?.tags || []}
        itemType={addTagDialogData?.itemType}
        itemName={addTagDialogData?.itemName}
        onClose={() => {
          setShowAddTagDialog(false);
          setAddTagDialogData(null);
        }}
        onSuccess={() => {
          setShowAddTagDialog(false);
          setAddTagDialogData(null);
          refetch();
        }}
        apiPath={`/event/${addTagDialogData?.itemName}/tags`}
      />

      <SectionHeader
        _deprecate_marginTop={0}
        title="Event Handler Definitions"
        actions={
          <SectionHeaderActions
            buttons={[
              {
                label: "Define event handler",
                onClick: () => pushHistory(EVENT_HANDLERS_URL.NEW),
                startIcon: <AddIcon />,
              },
            ]}
          />
        }
      />
      <SectionContainer>
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
                  placeholder="Search event handlers"
                  id="quick-search-field"
                  showClearButton
                  value={searchParam}
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
                      label="Active?:"
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
          <DataTable
            progressPending={isFetching}
            localStorageKey="eventHandlersTable"
            quickSearchEnabled
            conditionalRowStyles={conditionalRowStyles}
            quickSearchComponent={() => null}
            searchTerm={searchParam}
            onSearchTermChange={handleSearchTermChange}
            noDataComponent={
              searchParam === "" ? (
                <NoDataComponent
                  title="Event Handler"
                  description={INTRO_CONTENT}
                  buttonText="Define an event handler"
                  buttonHandler={handleClickDefineEventHandler}
                  disableButton={isTrialExpired}
                />
              ) : (
                <NoDataComponent
                  title="Empty"
                  titleBg={colors.warningTag}
                  description="I'm sorry that search didn't find any matches. Please try different filters."
                  buttonText="Clear search"
                  buttonHandler={() => handleSearchTermChange("")}
                />
              )
            }
            defaultShowColumns={["name", "event", "tags", "actions"]}
            keyField="name"
            data={activeNonActiveFiltered}
            columns={columns}
            customActions={[
              <Tooltip title="Refresh event handlers" key="refresh-tooltip">
                <Button
                  variant="text"
                  color="secondary"
                  sx={{
                    color: (theme) =>
                      theme.palette.mode === "dark"
                        ? colors.gray13
                        : colors.gray02,
                  }}
                  size="small"
                  startIcon={<RefreshIcon />}
                  key="refresh"
                  onClick={() => refetch()}
                >
                  Refresh
                </Button>
              </Tooltip>,
            ]}
            onChangePage={handlePageChange}
            paginationDefaultPage={pageParam ? Number(pageParam) : 1}
          />
        </Paper>
      </SectionContainer>
    </Box>
  );
}

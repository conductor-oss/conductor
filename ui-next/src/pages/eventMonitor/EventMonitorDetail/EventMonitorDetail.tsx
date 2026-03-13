import { Box, Grid, Stack } from "@mui/material";
import { Button, DataTable, Paper } from "components";
import { LegacyColumn } from "components/DataTable/types";
import Header from "components/Header";
import MuiButton from "components/MuiButton";
import MuiTypography from "components/MuiTypography";
import TagChip from "components/TagChip";
import { ConductorAutoComplete } from "components/v1";
import ConductorMultiSelect from "components/v1/ConductorMultiSelect";
import XCloseIcon from "components/v1/icons/XCloseIcon";
import { ConductorSectionHeader } from "components/v1/layout/section/ConductorSectionHeader";
import _countBy from "lodash/countBy";
import _get from "lodash/get";
import _isEmpty from "lodash/isEmpty";
import { useCallback, useEffect, useState } from "react";
import { Helmet } from "react-helmet";
import { Link, useParams } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "shared/SectionContainer";
import { EVENT_MONITOR_URL } from "utils/constants/route";
import { EventItem, GroupedEventItem, ModalConfig } from "../types";
import {
  actions,
  status,
  statusConfig,
  TIME_RANGE_OPTIONS,
  truncatePayload,
} from "../utils";
import { ExpandableGroupedItems, StatusBadge } from "./ExpandedGroupedItem";
import { PayloadModal } from "./PayloadModal";
import { RefreshEvent } from "./Refresher/RefreshEvent";
import { useRefreshMachine } from "./Refresher/state/hook";
import { PageType } from "./Refresher/state/types";

const StatusChips = ({ groupedItems }: { groupedItems?: EventItem[] }) => {
  if (!groupedItems?.length) return <span>N/A</span>;

  const counts = _countBy(groupedItems, "status");

  return (
    <Stack direction="row" spacing={1} flexWrap="wrap">
      {Object.entries(statusConfig)
        .filter(([key]) => counts[key])
        .map(([key, config]) => (
          <TagChip
            style={{ backgroundColor: config.color }}
            label={`${counts[key]} ${config.label}`}
            id={key}
            key={key}
          />
        ))}
    </Stack>
  );
};

const TruncatedPayload = ({
  payload,
  title,
  onViewMore,
}: {
  payload: object;
  title: string;
  onViewMore: (payload: object, title: string) => void;
}) => {
  return (
    <Box>
      <MuiTypography component="span">{truncatePayload(payload)}</MuiTypography>
      {JSON.stringify(payload).length > 100 && (
        <MuiButton
          variant="text"
          size="small"
          onClick={() => onViewMore(payload, title)}
        >
          View more
        </MuiButton>
      )}
    </Box>
  );
};

export const EventMonitorDetail = () => {
  const params = useParams();
  const eventName = decodeURIComponent(_get(params, "name") || "");
  const [timeRange, setTimeRange] = useQueryState(
    "from",
    TIME_RANGE_OPTIONS[0]?.value.toString() || "",
  );

  const [
    { eventMonitorData, isFetching, elapsed, refreshInterval },
    { changeRefreshRate, handleRefresh },
  ] = useRefreshMachine(PageType.EVENT_DETAIL, eventName, parseInt(timeRange));

  const [showPayloadModal, setShowPayloadModal] = useState(false);
  const [modalConfig, setModalConfig] = useState<ModalConfig | null>(null);

  const handleOpenModal = useCallback(
    (payload: EventItem, title = "Event Payload") => {
      setModalConfig({
        payload,
        title,
      });
      setShowPayloadModal(true);
    },
    [],
  );

  const handleCloseModal = useCallback(() => {
    setShowPayloadModal(false);
    setModalConfig(null);
  }, []);

  const [actionFilterParam, setActionFilterParam] = useQueryState(
    "actionFilter",
    "",
  );
  const [statusFilterParam, setStatusFilterParam] = useQueryState(
    "statusFilter",
    "",
  );

  const [actionFilter, setActionFilter] = useState<string[]>(() =>
    _isEmpty(actionFilterParam) ? [] : actionFilterParam.split(","),
  );

  const [statusFilter, setStatusFilter] = useState<string[]>(() =>
    _isEmpty(statusFilterParam) ? [] : statusFilterParam.split(","),
  );

  useEffect(() => {
    const newActionFilterParam = actionFilter.join(",");
    const newStatusFilterParam = statusFilter.join(",");

    if (newActionFilterParam !== actionFilterParam) {
      setActionFilterParam(newActionFilterParam);
    }
    if (newStatusFilterParam !== statusFilterParam) {
      setStatusFilterParam(newStatusFilterParam);
    }
  }, [
    actionFilter,
    statusFilter,
    actionFilterParam,
    statusFilterParam,
    setActionFilterParam,
    setStatusFilterParam,
  ]);

  const handleActionChange = useCallback(
    (values: string[], type: "action" | "status") => {
      if (type === "action") {
        setActionFilter((prev) =>
          values.length === prev.length && values.every((v, i) => v === prev[i])
            ? prev
            : values,
        );
      } else if (type === "status") {
        setStatusFilter((prev) =>
          values.length === prev.length && values.every((v, i) => v === prev[i])
            ? prev
            : values,
        );
      }
    },
    [],
  );

  const filterEventData = useCallback(
    (data: GroupedEventItem[]) => {
      if (_isEmpty(actionFilter) && _isEmpty(statusFilter)) {
        return data;
      }

      return data.filter((item) => {
        const groupedItems = item.groupedItems || [];
        return groupedItems.some(
          (groupItem) =>
            (_isEmpty(actionFilter) ||
              actionFilter.some(
                (filter) =>
                  filter.toLowerCase() === groupItem.action?.toLowerCase(),
              )) &&
            (_isEmpty(statusFilter) ||
              statusFilter.some(
                (filter) =>
                  filter.toLowerCase() === groupItem.status?.toLowerCase(),
              )),
        );
      });
    },
    [actionFilter, statusFilter],
  );
  const initColumns: LegacyColumn[] = [
    {
      id: "messageId",
      name: "messageId",
      label: "Message Id",
    },
    {
      id: "payload",
      name: "payload",
      label: "Payload",
      renderer: (val) => {
        const title = "Payload";
        return (
          <TruncatedPayload
            payload={val}
            title={title}
            onViewMore={() => handleOpenModal(val, title)}
          />
        );
      },
    },

    {
      id: "fullMessagePayload",
      name: "fullMessagePayload",
      label: "Full Message Payload",
      minWidth: "220px",
      renderer: (val) => {
        const title = "Message Payload";
        return (
          <TruncatedPayload
            payload={val}
            title={title}
            onViewMore={() => handleOpenModal(val, title)}
          />
        );
      },
      style: {
        div: {
          "&:first-child": {
            overflow: "unset",
          },
        },
      },
    },
    {
      id: "status",
      name: "status",
      label: "Status",
      right: true,
      renderer: (_val, row) => <StatusChips groupedItems={row.groupedItems} />,
    },
  ];

  return (
    <Box id="event-monitor-container">
      <Helmet>
        <title>Event Monitor Detail</title>
      </Helmet>
      <SectionContainer
        header={
          <ConductorSectionHeader
            breadcrumbItems={[
              { label: "Event Monitor", to: EVENT_MONITOR_URL.BASE },
              {
                label: eventName,
                to: "",
              },
            ]}
            title={eventName}
            buttonsComponent={
              <Button
                color="secondary"
                component={Link}
                to={EVENT_MONITOR_URL.BASE}
              >
                <XCloseIcon />
                &nbsp;Close
              </Button>
            }
          />
        }
      >
        {showPayloadModal && modalConfig && (
          <PayloadModal
            payload={JSON.stringify(modalConfig?.payload, null, 2)}
            title={modalConfig?.title}
            handleClose={handleCloseModal}
          />
        )}
        <Header loading={isFetching} />
        <Paper
          variant="outlined"
          sx={{
            overflow: "auto",
            padding: 5,
            borderRadius: 0,
          }}
        >
          <Box py={6} px={2}>
            <Grid container sx={{ width: "100%" }} spacing={6}>
              <Grid
                size={{
                  xs: 12,
                  sm: 12,
                  md: 4,
                }}
              >
                <ConductorAutoComplete
                  id="event-monitor-detail-dropdown"
                  fullWidth
                  label="Select action"
                  options={actions.map((action) => action.name)}
                  multiple
                  freeSolo
                  onChange={(__, val: string[]) =>
                    handleActionChange(val, "action")
                  }
                  value={actionFilter}
                />
              </Grid>

              <Grid
                size={{
                  xs: 12,
                  sm: 6,
                  md: 4,
                }}
              >
                <ConductorMultiSelect
                  dataTestId="event-detail-status-filter"
                  label="Select status"
                  options={status.map((stat) => stat.name)}
                  onSelected={(values) => handleActionChange(values, "status")}
                  value={statusFilter}
                  allText="All Status"
                  renderer={(option) => <StatusBadge status={option} />}
                />
              </Grid>
              <Grid
                size={{
                  xs: 12,
                  sm: 6,
                  md: 4,
                }}
              >
                <ConductorAutoComplete
                  dataTestId="event-detail-timer-filter"
                  fullWidth
                  label="Events within"
                  options={TIME_RANGE_OPTIONS.map((option) => option.label)}
                  onChange={(__, val) => {
                    const selectedOption = TIME_RANGE_OPTIONS.find(
                      (option) => option.label === val,
                    );
                    setTimeRange(selectedOption?.value.toString() || "");
                  }}
                  value={
                    TIME_RANGE_OPTIONS.find(
                      (option) => option.value.toString() === timeRange,
                    )?.label || null
                  }
                  isOptionEqualToValue={(option, currentValue) =>
                    option === currentValue
                  }
                  clearIcon={false}
                />
              </Grid>
              <RefreshEvent
                elapsed={elapsed}
                isFetching={isFetching}
                refreshInterval={refreshInterval}
                changeRefreshRate={changeRefreshRate}
                handleRefresh={handleRefresh}
              />
            </Grid>
          </Box>
          <DataTable
            title=""
            quickSearchEnabled={false}
            // progressPending={isFetching} //FIX ME
            defaultShowColumns={[
              "messageId",
              "payload",
              "status",
              "fullMessagePayload",
            ]}
            noDataComponent={
              <Box padding={5} fontWeight={600}>
                No action found for given event
              </Box>
            }
            hideSearch
            data={eventMonitorData ? filterEventData(eventMonitorData) : []}
            columns={initColumns}
            expandableRows
            expandableRowDisabled={(row) => row.groups?.length <= 0}
            expandableRowsComponent={({ data }) => (
              <ExpandableGroupedItems
                data={data}
                actionFilter={actionFilter}
                statusFilter={statusFilter}
                onOpenModal={handleOpenModal}
              />
            )}
          />
        </Paper>
      </SectionContainer>
    </Box>
  );
};

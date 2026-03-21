import { Grid } from "@mui/material";
import { Button, DataTable, NavLink } from "components";
import { LegacyColumn } from "components/DataTable/types";
import TagChip from "components/TagChip";
import { useMemo } from "react";
import { ExpanderComponentProps } from "react-data-table-component";
import { colors } from "theme/tokens/variables";
import { EventItem } from "../types";
import { statusColors, status as statusOptions } from "../utils";

interface GroupedData {
  groupedItems: EventItem[];
}
interface ExpandableGroupedItemsProps extends ExpanderComponentProps<GroupedData> {
  actionFilter: string[];
  statusFilter: string[];
  onOpenModal: (payload: any) => void;
}

export const StatusBadge = ({ status }: { status: string }) => {
  const currentStatus = statusOptions.find((s) => s.name === status);

  const color = currentStatus
    ? statusColors[currentStatus.name]
    : colors.greyBorder;
  const chipStyles = {
    backgroundColor: color,
  };

  return (
    <TagChip
      style={chipStyles}
      label={currentStatus ? currentStatus.label : "Unknown Status"}
      id={`${status}-chip`}
    />
  );
};

export const ExpandableGroupedItems = ({
  data,
  actionFilter,
  statusFilter,
  onOpenModal,
}: ExpandableGroupedItemsProps) => {
  const groupedColumns: LegacyColumn[] = [
    {
      id: "action",
      name: "action",
      label: "Action",
      sortable: false,

      renderer: (val, row) => (
        <Button variant="text" onClick={() => onOpenModal(row)}>
          {val}
        </Button>
      ),
    },

    {
      id: "status",
      name: "status",
      label: "status",
      sortable: false,
      grow: 0.5,
      renderer: (val) => <StatusBadge status={val} />,
    },
    {
      id: "statusDescription",
      name: "statusDescription",
      label: "Status Description",
      sortable: false,
      style: {
        div: {
          "&:first-child": {
            overflow: "unset",
          },
        },
      },
    },
    {
      id: "name",
      name: "name",
      label: "Event Handler",
      sortable: false,
      right: true,
      renderer: (val) => (
        <NavLink path={`/eventHandlerDef/${val}`}>{val}</NavLink>
      ),
    },
  ];

  const filteredItems = useMemo(() => {
    return data.groupedItems.filter((item: EventItem) => {
      const actionMatch =
        actionFilter.length === 0 ||
        actionFilter.some(
          (filter) =>
            filter.localeCompare(item.action, undefined, {
              sensitivity: "base",
            }) === 0,
        );
      const statusMatch =
        statusFilter.length === 0 ||
        statusFilter.some(
          (filter) =>
            filter.localeCompare(item.status, undefined, {
              sensitivity: "base",
            }) === 0,
        );
      return actionMatch && statusMatch;
    });
  }, [data.groupedItems, actionFilter, statusFilter]);
  return data ? (
    <Grid container sx={{ width: "100%" }} pl={12} mb={6}>
      <Grid size={12}>
        <DataTable
          title=""
          quickSearchEnabled={false}
          defaultShowColumns={["action", "status", "statusDescription", "name"]}
          data={filteredItems}
          columns={groupedColumns}
          noHeader
          pagination={false}
          highlightOnHover
        />
      </Grid>
    </Grid>
  ) : null;
};

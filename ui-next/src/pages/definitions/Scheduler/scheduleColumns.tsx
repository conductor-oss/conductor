import { Box, Tooltip } from "@mui/material";
import { NavLink } from "components";
import { ColumnCustomType } from "components/ui/DataTable/types";
import TagChip from "components/ui/TagChip";
import TagList from "components/ui/TagList";
import cronstrue from "cronstrue";
import { colors } from "theme/tokens/variables";
import { IScheduleDto, IStartWorkflowRequest } from "types/Schedulers";
import { TagDto } from "types/Tag";
import { SCHEDULER_DEFINITION_URL } from "utils/constants/route";
import { createSearchableTags } from "utils/utils";
import {
  activeLinkColor,
  getLinkColor,
  pausedLinkColor,
  pausedrowColor,
} from "../rowColorHelpers";

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

export const columns = [
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
    label: "Workflow/Agent",
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
    tooltip: "The workflow or agent associated with the schedule",
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

export const getDefaultShowColumns = (tagsEnabled: boolean) => [
  "name",
  "nextRunTime",
  "workflowExecutionsLink",
  "schedulerExecutionsLink",
  ...(tagsEnabled ? ["tags"] : []),
  "cronExpression",
  "startWorkflowRequest",
  "createTime",
  "paused",
  "actions",
];

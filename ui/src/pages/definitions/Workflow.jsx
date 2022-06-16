import React, { useMemo } from "react";
import { NavLink, DataTable, Button } from "../../components";
import { makeStyles } from "@material-ui/styles";
import _ from "lodash";
import { useQueryState } from "react-router-use-location-state";
import { useLatestWorkflowDefs } from "../../data/workflow";
import Header from "./Header";
import sharedStyles from "../styles";
import { Helmet } from "react-helmet";
import AddIcon from "@material-ui/icons/Add";

const useStyles = makeStyles(sharedStyles);

const columns = [
  {
    name: "name",
    renderer: (val) => (
      <NavLink path={`/workflowDef/${val.trim()}`}>{val.trim()}</NavLink>
    ),
  },
  { name: "description", grow: 2 },
  { name: "createTime", type: "date" },
  { name: "version", label: "Latest Version", grow: 0.5 },
  { name: "schemaVersion", grow: 0.5 },
  { name: "restartable", grow: 0.5 },
  { name: "workflowStatusListenerEnabled", grow: 0.5 },
  { name: "ownerEmail" },
  { name: "inputParameters", type: "json", sortable: false },
  { name: "outputParameters", type: "json", sortable: false },
  { name: "timeoutPolicy", grow: 0.5 },
  { name: "timeoutSeconds", grow: 0.5 },
  {
    id: "task_types",
    name: "tasks",
    label: "Task Types",
    searchable: "calculated",
    sortable: false,
    renderer: (val) => {
      const taskTypeSet = new Set();
      for (let task of val) {
        taskTypeSet.add(task.type);
      }
      return Array.from(taskTypeSet).join(", ");
    },
  },
  {
    id: "task_count",
    name: "tasks",
    label: "Tasks",
    searchable: "calculated",
    sortable: false,
    grow: 0.5,
    renderer: (val) => (_.isArray(val) ? val.length : 0),
  },
  {
    id: "executions_link",
    name: "name",
    label: "Executions",
    sortable: false,
    searchable: false,
    grow: 0.5,
    renderer: (name) => (
      <NavLink path={`/?workflowType=${name.trim()}`} newTab>
        Query
      </NavLink>
    ),
  },
];

export default function WorkflowDefinitions() {
  const classes = useStyles();

  const { data, isFetching } = useLatestWorkflowDefs();

  const [filterParam, setFilterParam] = useQueryState("filter", "");
  const filterObj = filterParam === "" ? undefined : JSON.parse(filterParam);

  const handleFilterChange = (obj) => {
    if (obj) {
      setFilterParam(JSON.stringify(obj));
    } else {
      setFilterParam("");
    }
  };

  const workflows = useMemo(() => {
    // Extract latest versions only
    if (data) {
      const unique = new Map();
      const types = new Set();
      for (let workflowDef of data) {
        if (!unique.has(workflowDef.name)) {
          unique.set(workflowDef.name, workflowDef);
        } else if (unique.get(workflowDef.name).version < workflowDef.version) {
          unique.set(workflowDef.name, workflowDef);
        }

        for (let task of workflowDef.tasks) {
          types.add(task.type);
        }
      }

      return Array.from(unique.values());
    }
  }, [data]);

  return (
    <div className={classes.wrapper}>
      <Helmet>
        <title>Conductor UI - Workflow Definitions</title>
      </Helmet>
      <Header tabIndex={0} loading={isFetching} />

      <div className={classes.tabContent}>
        <div className={classes.buttonRow}>
          <Button
            component={NavLink}
            path="/workflowDef"
            startIcon={<AddIcon />}
          >
            New Workflow Definition
          </Button>
        </div>

        {workflows && (
          <DataTable
            title={`${workflows.length} results`}
            localStorageKey="definitionsTable"
            defaultShowColumns={[
              "name",
              "description",
              "version",
              "createTime",
              "ownerEmail",
              "task_count",
              "executions_link",
            ]}
            keyField="name"
            onFilterChange={handleFilterChange}
            initialFilterObj={filterObj}
            data={workflows}
            columns={columns}
          />
        )}
      </div>
    </div>
  );
}

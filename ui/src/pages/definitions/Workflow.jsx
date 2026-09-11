import React, { useMemo, useState, useCallback } from "react";
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

  const [page, setPage] = useState(1);
  const [rowsPerPage, setRowsPerPage] = useState(15);

  const [filterParam, setFilterParam] = useQueryState("filter", "");
  const filterObj = filterParam === "" ? undefined : JSON.parse(filterParam);

  const serverFilter = useMemo(() => {
    if (filterObj && filterObj.columnName && filterObj.substring) {
      return {
        filterField: filterObj.columnName,
        filterValue: filterObj.substring,
      };
    }
    return null;
  }, [filterObj]);

  const pagination = { start: (page - 1) * rowsPerPage, size: rowsPerPage };

  const { data, isFetching } = useLatestWorkflowDefs(pagination, serverFilter);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const handleFilterChange = useCallback(
    _.debounce((obj) => {
      setPage(1);
      if (obj) {
        setFilterParam(JSON.stringify(obj));
      } else {
        setFilterParam("");
      }
    }, 300),
    []
  );

  const workflows = useMemo(() => {
    if (data) {
      return data.results || data;
    }
  }, [data]);

  const handlePageChange = useCallback((newPage) => {
    setPage(newPage);
  }, []);

  const handleRowsPerPageChange = useCallback((newPerPage) => {
    setRowsPerPage(newPerPage);
    setPage(1);
  }, []);

  const totalRows = data?.totalHits || 0;

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
            title={`${totalRows} results`}
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
            paginationServer
            paginationTotalRows={totalRows}
            paginationPerPage={rowsPerPage}
            paginationDefaultPage={page}
            onChangePage={handlePageChange}
            onChangeRowsPerPage={handleRowsPerPageChange}
          />
        )}
      </div>
    </div>
  );
}

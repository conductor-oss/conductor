import React from "react";
import { NavLink, DataTable } from "../../components";
import { makeStyles } from "@material-ui/styles";
import { useFetch } from "../../utils/query";
import Header from "./Header";
import sharedStyles from "../styles";
import { Helmet } from "react-helmet";

const useStyles = makeStyles(sharedStyles);

const columns = [
  {
    name: "name",
    renderer: (name) => (
      <NavLink path={`/eventHandlerDef/${name}`}>{name}</NavLink>
    ),
  },
  { name: "event" },
  { name: "createTime", type: "date" },
  {
    name: "actions",
    renderer: (val) => JSON.stringify(val.map((action) => action.action)),
  },
];

export default function TaskDefinitions() {
  const classes = useStyles();

  const { data: eventHandlers, isFetching } = useFetch("/event");

  return (
    <div className={classes.wrapper}>
      <Header tabIndex={2} loading={isFetching} />
      <Helmet>
        <title>Conductor UI - Event Handler Definitions</title>
      </Helmet>

      <div className={classes.tabContent}>
        {eventHandlers && (
          <DataTable
            title={`${eventHandlers.length} results`}
            localStorageKey="eventHandlersTable"
            defaultShowColumns={["name", "event", "actions"]}
            keyField="name"
            data={eventHandlers}
            columns={columns}
          />
        )}
      </div>
    </div>
  );
}

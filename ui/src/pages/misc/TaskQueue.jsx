import React from "react";
import { useRouteMatch } from "react-router-dom";
import sharedStyles from "../styles";
import { useTaskQueueInfo } from "../../utils/query";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import { useTaskNames } from "../../utils/query";
import { usePushHistory } from "../../components/NavLink";

import {
  Paper,
  DataTable,
  LinearProgress,
  Heading,
  Dropdown,
} from "../../components";
import _ from "lodash";

const useStyles = makeStyles(sharedStyles);

export default function TaskDefinition() {
  const taskNames = useTaskNames();
  const pushHistory = usePushHistory();
  const classes = useStyles();
  const match = useRouteMatch();
  const taskName = match.params.name || "";

  const { data, isFetching } = useTaskQueueInfo(taskName);

  const size = _.get(data, "size");
  const pollData = _.get(data, "pollData");

  function setTaskName(name) {
    if (name === null) {
      name = "";
    }
    pushHistory(`/taskQueue/${name}`);
  }

  return (
    <div className={classes.wrapper}>
      <Helmet>
        <title>Conductor UI - Task Queue</title>
      </Helmet>
      <div className={classes.header} style={{ paddingBottom: 20 }}>
        <Heading level={3} style={{ marginBottom: 30 }}>
          Task Queue Info
        </Heading>
        <Dropdown
          label="Select a Task Name"
          style={{ width: 500 }}
          options={taskNames}
          onChange={(evt, val) => setTaskName(val)}
          disableClearable
          getOptionSelected={(option, value) => {
            // Accept empty string
            if (value === "") return false;
            return value === option;
          }}
          value={taskName}
        />
      </div>
      {isFetching && <LinearProgress />}
      <div className={classes.tabContent}>
        {!_.isUndefined(size) && !_.isUndefined(pollData) && (
          <Paper>
            <Heading level={0} style={{ paddingLeft: 16, paddingTop: 30 }}>
              Queue Size: {size}{" "}
            </Heading>
            <DataTable
              title={`Workers: ${pollData.length}`}
              defaultShowColumns={["workerId", "domain", "lastPollTime"]}
              default
              data={pollData}
              columns={[
                { name: "workerId", label: "Worker ID" },
                { name: "domain", label: "Domain" },
                { name: "lastPollTime", label: "Last Poll Time", type: "date" },
              ]}
            />
          </Paper>
        )}
      </div>
    </div>
  );
}

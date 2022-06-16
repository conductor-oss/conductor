import { useRouteMatch } from "react-router-dom";
import sharedStyles from "../styles";
import { usePollData, useQueueSizes, useTaskNames } from "../../data/task";
import { makeStyles } from "@material-ui/styles";
import { Helmet } from "react-helmet";
import { usePushHistory } from "../../components/NavLink";
import { formatRelative } from "date-fns";

import {
  Paper,
  DataTable,
  LinearProgress,
  Heading,
  Dropdown,
} from "../../components";
import _ from "lodash";
import { timestampRenderer } from "../../utils/helpers";

const useStyles = makeStyles(sharedStyles);

function getSizesMap(sizes) {
  const retval = new Map();
  for (let row of sizes) {
    if (row.isSuccess) {
      retval.set(row.data.domain, row.data.size);
    }
  }
  return retval;
}

export default function TaskDefinition() {
  const taskNames = useTaskNames();
  const pushHistory = usePushHistory();
  const classes = useStyles();
  const match = useRouteMatch();
  const taskName = match.params.name || "";

  const { data: pollData, isFetching } = usePollData(taskName);
  const domains = pollData ? pollData.map((row) => row.domain) : null;
  const sizes = useQueueSizes(taskName, domains);
  const sizesMap = getSizesMap(sizes);
  const now = new Date();

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
          Task Queues
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
        {!_.isUndefined(pollData) && (
          <Paper>
            <DataTable
              title="Poll Status by Domain"
              defaultShowColumns={[
                "workerId",
                "domain",
                "lastPollTime",
                "queueSize",
              ]}
              default
              data={pollData}
              columns={[
                {
                  name: "domain",
                  label: "Domain",
                  renderer: (domain) =>
                    _.isEmpty(domain) ? "(Domain not set)" : domain,
                },
                { name: "workerId", label: "Last Polled Worker" },
                {
                  name: "lastPollTime",
                  label: "Last Poll Time",
                  renderer: (time) =>
                    `${timestampRenderer(time)} (${formatRelative(time, now)})`,
                },
                {
                  name: "domain",
                  id: "queueSize",
                  label: "Queue Size",
                  renderer: (domain) => sizesMap.get(domain),
                },
              ]}
            />
          </Paper>
        )}
      </div>
    </div>
  );
}

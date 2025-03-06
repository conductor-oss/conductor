import { useState } from "react";
import { Tabs, Tab, Paper } from "../../components";
import Timeline from "./Timeline";
import TaskList from "./TaskList";
import { makeStyles } from "@material-ui/styles";
import { WorkflowVisualizerJson } from "orkes-workflow-visualizer";
import {
  pendingTaskSelection,
  taskWithLatestIteration,
} from "../../utils/helpers";
import { useFetchForWorkflowDefinition } from "../../utils/helperFunctions";
import PanAndZoomWrapper from "../../components/diagram/PanAndZoomWrapper";

const useStyles = makeStyles({
  taskWrapper: {
    overflowY: "auto",
    padding: 30,
    height: "100%",
  },
});

export default function TaskDetails({
  execution,
  dag,
  selectedTask,
  setSelectedTask,
  setSelectedNode,
}) {
  const [tabIndex, setTabIndex] = useState(0);
  // For PanAndZoomWrapper
  const [layout, setLayout] = useState({ height: 0, width: 0 });

  const handleSetLayout = (value) => {
    setLayout((prevLayout) => {
      if (
        prevLayout?.width === value?.width &&
        prevLayout?.height === value?.height
      ) {
        return prevLayout;
      }
      return value;
    });
  };
  //
  const classes = useStyles();
  const { fetchForWorkflowDefinition, extractSubWorkflowNames } =
    useFetchForWorkflowDefinition();

  return (
    <div className={classes.taskWrapper}>
      <Paper>
        <Tabs value={tabIndex} contextual>
          <Tab label="Diagram" onClick={() => setTabIndex(0)} />
          <Tab label="Task List" onClick={() => setTabIndex(1)} />
          <Tab label="Timeline" onClick={() => setTabIndex(2)} />
        </Tabs>

        {tabIndex === 0 && (
          <div style={{ height: "calc(100vh - 300px)" }}>
            <PanAndZoomWrapper
              layout={layout}
              workflowName={execution?.workflowName}
            >
              <WorkflowVisualizerJson
                data={execution}
                executionMode={true}
                onClick={(e, data) => {
                  const selectedTaskRefName =
                    data?.data?.task?.executionData?.status === "PENDING"
                      ? pendingTaskSelection(data?.data?.task)?.workflowTask
                          ?.taskReferenceName
                      : taskWithLatestIteration(execution?.tasks, {
                          ref: data.id,
                        })?.referenceTaskName;
                  setSelectedNode(data);
                  setSelectedTask({ ref: selectedTaskRefName });
                }}
                subWorkflowFetcher={async (workflowName, version) =>
                  await fetchForWorkflowDefinition({
                    workflowName: workflowName,
                    currentVersion: version,
                    collapseWorkflowList: extractSubWorkflowNames(
                      execution?.workflowDefinition
                    ),
                  })
                }
                handleLayoutChange={(value) => {
                  if (value != null && value.width != null) {
                    handleSetLayout(value);
                  }
                }}
              />
            </PanAndZoomWrapper>
          </div>
        )}
        {tabIndex === 1 && (
          <TaskList
            workflowId={execution.workflowId}
            selectedTask={selectedTask}
            tasks={execution.tasks}
            dag={dag}
            onClick={setSelectedTask}
          />
        )}
        {tabIndex === 2 && (
          <Timeline
            selectedTask={selectedTask}
            tasks={execution.tasks}
            dag={dag}
            onClick={setSelectedTask}
          />
        )}
      </Paper>
    </div>
  );
}

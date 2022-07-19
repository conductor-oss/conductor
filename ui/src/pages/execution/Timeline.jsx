import React, { useMemo } from "react";
import Timeline from "react-vis-timeline-2";
import { timestampRenderer, durationRenderer } from "../../utils/helpers";
import _ from "lodash";
import "./timeline.scss";
import ZoomOutMapIcon from "@material-ui/icons/ZoomOutMap";
import { IconButton, Tooltip } from "@material-ui/core";

export default function TimelineComponent({
  dag,
  tasks,
  onClick,
  selectedTask,
}) {
  const timelineRef = React.useRef();
  /*
  const selectedId = useMemo(() => {
    if(selectedTask){
      const taskResult = dag.resolveTaskResult(selectedTask);
      return _.get(taskResult, "taskId")
    }
  }, [dag, selectedTask]);
  */
  const selectedId = null;

  const { items, groups } = useMemo(() => {
    const groupMap = new Map();
    for (const task of tasks) {
      groupMap.set(task.referenceTaskName, {
        id: task.referenceTaskName,
        content: `${task.referenceTaskName} (${task.workflowTask.name})`,
      });
    }

    const items = tasks
      .filter((t) => t.startTime > 0 || t.endTime > 0)
      .map((task) => {
        const dfParent = dag.graph
          .predecessors(task.referenceTaskName)
          .map((t) => dag.graph.node(t))
          .find((t) => t.type === "FORK_JOIN_DYNAMIC");
        const startTime =
          task.startTime > 0
            ? new Date(task.startTime)
            : new Date(task.endTime);
        const endTime =
          task.endTime > 0 ? new Date(task.endTime) : new Date(task.startTime);
        const duration = durationRenderer(
          endTime.getTime() - startTime.getTime()
        );
        const retval = {
          id: task.taskId,
          group: task.referenceTaskName,
          content: `${duration}`,
          start: startTime,
          end: endTime,
          title: `${task.referenceTaskName} (${
            task.status
          })<br/>${timestampRenderer(startTime)} - ${timestampRenderer(
            endTime
          )}`,
          className: `status_${task.status}`,
        };

        if (dfParent || task.type === "FORK_JOIN_DYNAMIC") {
          //retval.subgroup=task.referenceTaskName
          const gp = groupMap.get(dfParent.ref);
          if (!gp.nestedGroups) {
            gp.nestedGroups = [];
          }
          groupMap.get(task.referenceTaskName).treeLevel = 2;
          gp.nestedGroups.push(task.referenceTaskName);
        }

        return retval;
      });

    return {
      items: items,
      groups: Array.from(groupMap.values()),
    };
  }, [tasks, dag]);

  const onFit = () => {
    timelineRef.current.timeline.fit();
  };

  const handleClick = (e) => {
    const { group, item, what } = e;
    if (group && what !== "background") {
      if (_.size(dag.graph.node(group).taskResults) > 1) {
        onClick({
          ref: group,
          taskId: item,
        });
      } else {
        onClick({ ref: group });
      }
    }
  };

  return (
    <div>
      <div style={{ marginLeft: 15 }}>
        Ctrl-scroll to zoom.{" "}
        <Tooltip title="Zoom to Fit">
          <IconButton onClick={onFit}>
            <ZoomOutMapIcon />
          </IconButton>
        </Tooltip>
      </div>
      <div className="timeline-container">
        <Timeline
          ref={timelineRef}
          initialGroups={groups}
          initialItems={items}
          selection={selectedId}
          clickHandler={handleClick}
          options={{
            orientation: "top",
            zoomKey: "ctrlKey",
            type: "range",
            stack: false,
          }}
        />
      </div>
      <br />
    </div>
  );
}

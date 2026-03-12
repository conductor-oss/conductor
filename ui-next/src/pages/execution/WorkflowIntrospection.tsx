import React, { FunctionComponent, JSX, useContext, useState } from "react";
import { Box, Stack, Tooltip } from "@mui/material";
import {
  DetailedTime,
  ExecutionTask,
  WorkflowExecution,
  WorkflowIntrospectionRecord,
} from "types/Execution";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { LegacyColumn } from "components/DataTable/types";
import { colors } from "theme/tokens/variables";
import { DataTable } from "components/index";
import Dropdown from "components/Dropdown";
import { clickHandler, taskIdRenderer } from "pages/execution/componentHelpers";
import { StackTraceComponent } from "components/StackTrace";

interface WorkflowIntrospectionProps {
  selectTask: (taskSel: { ref?: string; taskId?: string }) => void;
  workflow: WorkflowExecution;
}

function formatDetailedTime(time: DetailedTime) {
  if (!time) return "";

  const parts = [];

  const micros = Math.floor(time.nanos / 1_000);
  const millis = Math.floor(micros / 1_000);

  if (Math.floor(time.seconds) > 0) {
    parts.push(`${Math.floor(time.seconds)} seconds`);
  }

  if (millis > 0) {
    parts.push(`${millis}ms`);
  }

  if (micros > 0) {
    parts.push(`${micros % 1_000}μs`);
  }

  return parts.join(" ");
}

function compareDetailedTime(
  a: DetailedTime,
  b: DetailedTime,
  ascending: boolean = true,
): number {
  if (a.seconds !== b.seconds) {
    return ascending ? a.seconds - b.seconds : b.seconds - a.seconds;
  } else {
    return ascending ? a.nanos - b.nanos : b.nanos - a.nanos;
  }
}

function nanos(time: DetailedTime) {
  return time.seconds * 1e9 + time.nanos;
}

function getColor(
  colorMap: Map<string, number>,
  records: Map<string, TreeNode>,
  record: WorkflowIntrospectionRecord,
) {
  const colorKey = `${record.id}-${record.threadName}`;

  if (colorMap.has(colorKey)) {
    return colorMap.get(colorKey) || 123;
  } else if (record.parentRecordId && records.has(record.parentRecordId)) {
    return getColor(
      colorMap,
      records,
      records.get(record.parentRecordId)!.record,
    );
  } else {
    return 123;
  }
}

class Tree {
  roots: TreeNode[] = [];
  records: Map<string, TreeNode> = new Map<string, TreeNode>();

  add(root: TreeNode) {
    this.roots.push(root);
    this.records.set(root.record.id, root);
  }
}

function duration(nodes: TreeNode[]) {
  let duration = 0;

  for (const node of nodes) {
    duration = Math.max(duration, nanos(node.record.duration));
  }

  return duration;
}

class TreeNode {
  parent: TreeNode | null = null;
  record: WorkflowIntrospectionRecord;
  children: TreeNode[] = [];
  overlapArr: TreeNode[];
  overlapSet = new Set<TreeNode>();
  threadArray: string[] = [];
  threadMap = new Map<string, TreeNode>();

  constructor(record: WorkflowIntrospectionRecord) {
    this.record = record;
    this.overlapArr = [this];
    this.overlapSet.add(this);
  }

  sort() {
    this.threadArray.sort((a, b) => {
      const aNode = this.threadMap.get(a);
      const bNode = this.threadMap.get(b);

      if (aNode && bNode) {
        return compareDetailedTime(
          aNode.record.duration,
          bNode.record.duration,
          false,
        );
      } else if (aNode) {
        return -1;
      } else if (bNode) {
        return 1;
      } else {
        return 0;
      }
    });
  }

  add(child: TreeNode) {
    child.parent = this;
    this.children.push(child);

    if (!this.threadMap.has(child.record.threadName)) {
      this.threadMap.set(child.record.threadName, child);
      this.threadArray.push(child.record.threadName);
    }

    this.sort();
  }

  addOverlap(node: TreeNode) {
    if (!this.overlapSet.has(node)) {
      this.overlapArr.push(node);
      this.overlapArr.sort((a, b) =>
        compareDetailedTime(a.record.duration, b.record.duration, false),
      );
    }

    if (!node.overlapSet.has(this)) {
      node.overlapArr.push(node);
      node.overlapArr.sort((a, b) =>
        compareDetailedTime(a.record.duration, b.record.duration, false),
      );
    }
  }

  hasLeaf() {
    if (this.record.attributes && this.record.attributes["isLeaf"] === true) {
      return true;
    }

    for (const thread of this.threadMap.values()) {
      if (thread.hasLeaf()) {
        return true;
      }
    }

    return false;
  }

  size() {
    let size = 1;

    for (const thread of this.threadMap.values()) {
      size += thread.size();
    }

    return size;
  }

  depth() {
    const threadRecord = Object.groupBy(
      this.overlapArr,
      (node) => node.record.threadName,
    );
    const threads: TreeNode[][] = [];

    for (const thread of Object.values(threadRecord)) {
      if (thread) {
        threads.push(thread);
      }
    }

    threads.sort((a, b) => duration(b) - duration(a));

    const nodes: TreeNode[] = [];

    for (const thread of threads) {
      nodes.push(...thread);
    }

    return nodes.indexOf(this);
  }
}

export const WorkflowIntrospection: FunctionComponent<
  WorkflowIntrospectionProps
> = ({ workflow, selectTask }) => {
  const { mode } = useContext(ColorModeContext);

  let minTime = Number.POSITIVE_INFINITY;
  let maxTime = Number.NEGATIVE_INFINITY;

  const tree = new Tree();

  let leafDuration = 0;
  const leafRanges: { start: number; end: number; node: TreeNode }[] = [];
  const nodeRanges: { start: number; end: number; node: TreeNode }[] = [];

  for (const record of workflow.workflowIntrospection || []) {
    const node = new TreeNode(record);

    tree.records.set(record.id, node);
    nodeRanges.push({
      start: nanos(record.start),
      end: nanos(record.start) + nanos(record.duration),
      node: node,
    });

    if (record.attributes && record.attributes["isLeaf"] === true) {
      leafRanges.push({
        start: nanos(record.start),
        end: nanos(record.start) + nanos(record.duration),
        node: node,
      });
    }

    minTime = Math.min(minTime, nanos(record.start));
    maxTime = Math.max(maxTime, nanos(record.start) + nanos(record.duration));
  }

  // Shift all times to start at 0
  for (const leaf of leafRanges) {
    leaf.start -= minTime;
    leaf.end -= minTime;
  }

  for (const range of nodeRanges) {
    for (const other of nodeRanges) {
      if (range === other) continue;

      if (range.start >= other.start && range.end <= other.end) {
        // Leaf time is fully contained within another leaf time executing in parallel
        range.node.addOverlap(other.node);
      } else if (range.start < other.start && range.end >= other.start) {
        // [------------------] <- leaf
        //        [------------------] <- other
        // [-----] <- leaf (duration adjusted for overlap)
        range.node.addOverlap(other.node);
      } else if (
        range.start > other.start &&
        range.end >= other.end &&
        range.start < other.end
      ) {
        //        [------------------] <- leaf
        // [------------------] <- other
        //                     [-----] <- leaf (duration adjusted for overlap)
        range.node.addOverlap(other.node);
      } else {
        // Leaves are fully disjointed
      }
    }
  }

  for (const leaf of leafRanges) {
    for (const other of leafRanges) {
      if (leaf === other || leaf.end === 0 || other.end === 0) continue;

      if (leaf.start >= other.start && leaf.end <= other.end) {
        // Leaf time is fully contained within another leaf time executing in parallel
        leaf.start = leaf.end = 0;
        break;
      } else if (leaf.start < other.start && leaf.end >= other.start) {
        // [------------------] <- leaf
        //        [------------------] <- other
        // [-----] <- leaf (duration adjusted for overlap)
        other.start = leaf.start;
        leaf.start = leaf.end = 0;
        break;
      } else if (
        leaf.start > other.start &&
        leaf.end >= other.end &&
        leaf.start < other.end
      ) {
        //        [------------------] <- leaf
        // [------------------] <- other
        //                     [-----] <- leaf (duration adjusted for overlap)
        other.end = leaf.end;
        leaf.start = leaf.end = 0;
      } else {
        // Leaves are fully disjointed
      }
    }
  }

  for (const leaf of leafRanges) {
    leafDuration += Math.max(0, leaf.end - leaf.start);
  }

  for (const record of workflow.workflowIntrospection || []) {
    const node = tree.records.get(record.id)!;

    if (record.parentRecordId) {
      const parent = tree.records.get(record.parentRecordId);

      if (parent) {
        parent.add(node);
      } else {
        console.error(
          `Parent record ${record.parentRecordId} not found for record ${record.id}`,
        );
      }
    } else {
      tree.add(node);
    }
  }
  const highlight = {
    backgroundColor: "rgba(0, 0, 0, 0.1)",
  };

  const roots: JSX.Element[] = [];

  const [selected, setHovered] = React.useState<string | null>(null);
  const [hideShortOperations, setHideShortOperations] = useState<string>(
    "Hide Short Operations",
  );

  const totalDuration = maxTime - minTime;
  const threadNames = new Map<string, { set: Set<string>; arr: string[] }>();

  let maxDepth = 0;

  for (const record of workflow.workflowIntrospection || []) {
    const parent = tree.records.get(record.parentRecordId || "");

    if (parent) {
      if (!threadNames.has(parent.record.id)) {
        threadNames.set(parent.record.id, { set: new Set<string>(), arr: [] });
      }

      const entry = threadNames.get(parent.record.id)!;

      if (!entry.set.has(record.threadName)) {
        entry.set.add(record.threadName);
        entry.arr.push(record.threadName);
      }
    }
  }

  const colorsMap = new Map<string, number>();
  let colorCount = 0;

  for (const record of workflow.workflowIntrospection || []) {
    if (record.parentRecordId) {
      const parentThreads = threadNames.get(record.parentRecordId);

      if (parentThreads && parentThreads.set.size > 1) {
        ++colorCount;

        colorsMap.set(
          `${record.id}-${record.threadName}`,
          123 + colorCount++ * 13,
        );
      }
    }
  }

  const rows = [];
  const threads = new Map<
    string,
    { threadElements: JSX.Element[]; children: Map<string, JSX.Element[]> }
  >();

  for (const record of workflow.workflowIntrospection || []) {
    const duration = nanos(record.duration);
    const parent = tree.records.get(record.parentRecordId || "") || null;
    const widthPercentage = (duration / totalDuration) * 100;
    const node = tree.records.get(record.id)!;

    if (
      hideShortOperations === "Hide Short Operations" &&
      widthPercentage < 2 &&
      !node.hasLeaf()
    ) {
      continue;
    }

    rows.push(record);

    const width = `${widthPercentage}%`;

    if (parent && !threads.has(parent.record.id)) {
      threads.set(parent.record.id, {
        threadElements: [],
        children: new Map<string, JSX.Element[]>(),
      });
    }

    if (!threads.has(record.id)) {
      threads.set(record.id, {
        threadElements: [],
        children: new Map<string, JSX.Element[]>(),
      });
    }

    const hover = (event: React.MouseEvent<HTMLDivElement, MouseEvent>) => {
      event.stopPropagation();

      setHovered(record.id);
    };

    const hue = getColor(colorsMap, tree.records, record);
    const backgroundColor =
      selected === record.id
        ? `hsl(${hue},14%,54%)`
        : `hsl(${hue + 7},47%,74%)`;
    const leftPercentage = (nanos(record.start) - minTime) / totalDuration;

    const depth = node.depth() || 0;

    maxDepth = Math.max(maxDepth, depth);

    const border = "1px solid rgba(0, 0, 0, 0.12";

    const headerStyle = {
      fontWeight: "bold",
      margin: 0,
      backgroundColor: mode === "dark" ? colors.gray04 : colors.gray14,
      borderRight: border,
      padding: "5px 10px 5px 10px",
      whiteSpace: "nowrap",
    };

    const itemStyle = {
      whiteSpace: "nowrap",
      margin: 0,
      padding: "5px 10px 5px 5px",
    };

    const hasDescription =
      record.description && record.description.trim().length > 0;

    const borderRadius = "5px";

    const attributes: JSX.Element[] = [];

    if (record.attributes) {
      for (const [key, value] of Object.entries(record.attributes)) {
        let headerText = key.replace(/([A-Z_])/g, " $1");

        headerText = headerText.charAt(0).toUpperCase() + headerText.slice(1);

        attributes.push(
          <>
            <p style={headerStyle}>{headerText}: </p>
            <p style={itemStyle}>{String(value)}</p>
          </>,
        );
      }
    }

    const tooltip = (
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "min-content auto",
          columnGap: "10px",
          width: "fit-content",
          inlineSize: "max-content",
          alignItems: "center",
        }}
      >
        <p style={{ borderTopLeftRadius: borderRadius, ...headerStyle }}>
          Name:{" "}
        </p>
        <p style={itemStyle}>{record.name}</p>
        <p style={headerStyle}>ID: </p>
        <p style={itemStyle}>{record.id}</p>
        <p style={headerStyle}>Thread: </p>
        <p style={itemStyle}>{record.threadName}</p>
        {attributes}
        <p
          style={{
            borderBottomLeftRadius: hasDescription ? 0 : borderRadius,
            ...headerStyle,
          }}
        >
          Duration:{" "}
        </p>
        <p style={itemStyle}>{formatDetailedTime(record.duration)}</p>
        <p
          style={{
            borderTop: border,
            gridColumn: "1 / -1",
            overflowWrap: "break-word",
            inlineSize: "100%",
            margin: 0,
            padding: "10px",
            contain: "inline-size",
            display: hasDescription ? "inline" : "none",
          }}
        >
          {record.description}
        </p>
      </div>
    );

    const scroll = (event: React.MouseEvent) => {
      event.stopPropagation();

      document
        .getElementById(`row-${record.id}`)
        ?.scrollIntoView({ behavior: "smooth", block: "center" });
    };

    const element = (
      <Tooltip
        followCursor={true}
        title={tooltip}
        x-data-name={record.name}
        x-data-id={record.id}
        x-data-depth={`${depth}`}
        x-data-thread={node.record.threadName}
        onMouseEnter={hover}
        style={{
          display: "flex",
          flexDirection: "row",
          overflow: "hidden",
          backgroundColor: backgroundColor,
          borderRadius: "1ex",
          padding: "3px 0 3px 0",
          border: "1px solid black",
          width: width,
          left: `${leftPercentage * 100}%`,
          position: "absolute",
          bottom: `${depth * 30}px`,
          cursor: "pointer",
        }}
        slotProps={{
          popper: {
            placement: "left",
          },
          tooltip: {
            style: {
              width: "fit-content",
              maxWidth: "fit-content",
              fontSize: "13px",
              fontWeight: 300,
              backgroundColor:
                mode === "dark" ? colors.grayTableBackground : colors.white,
              color: mode === "dark" ? colors.gray12 : colors.gray02,
              boxShadow: "4px 4px 10px 0px #59595969",
              padding: 0,
              borderRadius: borderRadius,
            },
          },
        }}
      >
        <div onClick={scroll}>
          <p
            style={{
              margin: 0,
              marginLeft: "5px",
              flexFlow: "column wrap",
              whiteSpace: "nowrap",
              pointerEvents: "none",
              userSelect: "none",
            }}
          >
            {record.name}
          </p>
          <p
            style={{
              margin: 0,
              marginLeft: "20px",
              marginRight: "5px",
              textAlign: "right",
              flexFlow: "column wrap",
              whiteSpace: "nowrap",
              flex: 1,
              pointerEvents: "none",
              userSelect: "none",
            }}
          >
            {formatDetailedTime(record.duration)}
          </p>
        </div>
      </Tooltip>
    );

    roots.push(element);
  }

  const detailedFullDuration = {
    seconds: Math.floor((maxTime - minTime) / 1e9),
    nanos: (maxTime - minTime) % 1e9,
  };

  const detailedLeafDuration = {
    seconds: Math.floor(leafDuration / 1e9),
    nanos: leafDuration % 1e9,
  };

  const detailedOverheadDuration = {
    seconds: Math.floor((maxTime - minTime - leafDuration) / 1e9),
    nanos: Math.floor((maxTime - minTime - leafDuration) % 1e9),
  };

  const defaultStyle = {
    transition: "0.1s ease",
    padding: "7.5px",
  };

  const workflowIntrospectionFields: LegacyColumn[] = [
    {
      id: "name",
      name: "name",
      label: "Name",
      minWidth: "300px",
      width: "300px",
      maxWidth: "300px",
      style: {
        fontSize: "13px",
        whiteSpace: "nowrap",
        wordBreak: "keep-all",
        flex: 1,
        ...defaultStyle,
      },
      tooltip: "The name of the operation Conductor is performing",
      conditionalCellStyles: [],
    },
    {
      id: "id",
      name: "id",
      label: "ID",
      maxWidth: "300px",
      minWidth: "300px",
      tooltip: "The unique identifier of the operation",
      style: defaultStyle,
      center: true,
      conditionalCellStyles: [],
    },
    {
      id: "duration",
      name: "duration",
      label: "Duration",
      width: "200px",
      tooltip: "The duration of the operation",
      renderer: formatDetailedTime,
      sortFunction: (
        a: WorkflowIntrospectionRecord,
        b: WorkflowIntrospectionRecord,
      ) => compareDetailedTime(a.duration, b.duration),
      style: { whiteSpace: "nowrap", ...defaultStyle },
      right: true,
      conditionalCellStyles: [],
    },
    {
      id: "overhead",
      name: "overhead",
      label: "Overhead",
      width: "200px",
      tooltip: "The overhead time of the operation (excludes child durations)",
      renderer: formatDetailedTime,
      sortFunction: (
        a: WorkflowIntrospectionRecord,
        b: WorkflowIntrospectionRecord,
      ) => compareDetailedTime(a.overhead, b.overhead),
      style: { whiteSpace: "nowrap", ...defaultStyle },
      right: true,
      conditionalCellStyles: [],
    },
    {
      id: "threadName",
      name: "threadName",
      label: "Thread",
      width: "200px",
      tooltip: "A name for the logical thread that this operation was part of",
      style: defaultStyle,
      conditionalCellStyles: [],
    },
    {
      id: "parentRecordId",
      name: "parentRecordId",
      label: "Parent",
      minWidth: "300px",
      tooltip: "The parent of this operation",
      style: defaultStyle,
      center: true,
      conditionalCellStyles: [],
    },
    {
      id: "taskId",
      name: "taskId",
      label: "Task ID",
      tooltip: "The unique identifier of the task, if applicable",
      renderer: (taskId: string, row: ExecutionTask) => {
        if (!taskId || taskId.trim().length === 0) {
          return "";
        }

        const renderer = taskIdRenderer(
          clickHandler((task) => {
            selectTask({ taskId: task.taskId });
          }),
        );

        return renderer.apply(renderer, [taskId, row]);
      },
      center: true,
      conditionalCellStyles: [],
    },
    {
      id: "stacktrace",
      name: "stacktrace",
      label: "Stack Trace",
      tooltip: "The specific line of code that initiated this operation",
      conditionalCellStyles: [],
      format: (row: WorkflowIntrospectionRecord) => (
        <StackTraceComponent stacktrace={row.stacktrace} />
      ),
    },
    {
      id: "description",
      name: "description",
      label: "Description",
      maxWidth: "400px",
      tooltip: "Additional information about the operation",
      style: { flex: 1, ...defaultStyle },
      conditionalCellStyles: [],
      format: (row: WorkflowIntrospectionRecord) =>
        row.description?.split("\n").map((line, _index) => (
          <p key={line} style={{ margin: 0, whiteSpace: "wrap" }}>
            {line}
          </p>
        )),
    },
  ];

  const chartHeight = (maxDepth + 1) * 30;

  return (
    <Stack
      sx={{
        padding: 2,
        display: "flex",
        gap: "10px",
        width: "100%",
        height: "100%",
      }}
      spacing={5}
    >
      <Box
        sx={{
          backgroundColor:
            mode === "dark" ? colors.grayTableBackground : colors.white,
        }}
      >
        <h3
          style={{
            marginTop: 0,
            marginBottom: 0,
            borderBottom: "1px solid #eee",
            padding: "10px",
          }}
        >
          Summary
        </h3>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "auto auto",
            columnGap: "20px",
            rowGap: "5px",
            marginBottom: "10px",
            width: "fit-content",
            padding: "10px",
          }}
        >
          <p style={{ margin: 0, fontWeight: "bold" }}>Workflow Duration:</p>
          <p style={{ margin: 0 }}>
            {formatDetailedTime(detailedFullDuration)}
          </p>
          <p style={{ margin: 0, fontWeight: "bold" }}>
            User Operation Duration:
          </p>
          <p style={{ margin: 0 }}>
            {formatDetailedTime(detailedLeafDuration)}
          </p>
          <p style={{ margin: 0, fontWeight: "bold" }}>Conductor Overhead:</p>
          <p style={{ margin: 0 }}>
            {formatDetailedTime(detailedOverheadDuration)}
          </p>
        </div>
      </Box>
      <Box
        sx={{
          backgroundColor:
            mode === "dark" ? colors.grayTableBackground : colors.white,
          margin: 0,
          display: "block",
          flexDirection: "row",
          alignItems: "end",
          gap: "1px",
          padding: "10px",
          maxHeight: "450px",
          height: `${Math.min(chartHeight + 20, 450)}px`,
          minHeight: 0,
          position: "relative",
          overflow: "scroll",
          alignContent: "end",
        }}
      >
        <div
          children={roots}
          style={{
            position: "relative",
            width: "100%",
            height: `${chartHeight}px`,
          }}
        ></div>
      </Box>
      <Box
        sx={{
          backgroundColor:
            mode === "dark" ? colors.grayTableBackground : colors.white,
          minHeight: 0,
          maxHeight: "800px",
          flex: 1,
          marginTop: 0,
          position: "relative",
        }}
      >
        <DataTable
          fixedHeader
          data={rows}
          pagination={false}
          onRowMouseEnter={(row) => setHovered(row.id)}
          customActions={[
            <Dropdown
              key="hide-short-operations"
              options={["Hide Short Operations", "Show Short Operations"]}
              value={hideShortOperations}
              onChange={(_, val) => {
                if (typeof val === "string") {
                  setHideShortOperations(val);
                } else {
                  console.warn("Expected string value from dropdown");
                }
              }}
              style={{ width: "225px" }}
            />,
          ]}
          customStyles={{
            responsiveWrapper: {
              style: {
                maxHeight: "100%",
              },
            },
            rows: {
              highlightOnHoverStyle: highlight,
              style: (row: WorkflowIntrospectionRecord) => ({
                backgroundColor:
                  selected === row.id ? highlight.backgroundColor : "inherit",
              }),
            },
          }}
          columns={
            workflowIntrospectionFields.map((col) => ({
              ...col,
              conditionalCellStyles: [
                {
                  when: (row: WorkflowIntrospectionRecord) =>
                    selected === row.id,
                  style: {
                    backgroundColor: highlight.backgroundColor,
                  },
                },
              ],
            })) as LegacyColumn[]
          }
          defaultShowColumns={["name", "overhead", "description", "threadName"]}
          localStorageKey="workflowIntrospectionTable"
          sortByDefault={false}
        />
      </Box>
    </Stack>
  );
};

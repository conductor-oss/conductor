import { CSSProperties, useContext, useState } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import { TaskStatus } from "types";
import { ExecutionTask } from "types/Execution";
import { getFlowTheme } from "components/features/flow/theme";

/**
 * Tasks that ran inside this workflow for this task without being steps of the definition — a
 * guardrail detector, say.
 *
 * They are drawn beside the node rather than in the flow, because the workflow never waits on them
 * and putting them in the graph would push the DAG around. Everything here lives inside the node's
 * existing `<foreignObject>`, which is already `overflow: visible`, so the node keeps the size ELK
 * measured and nothing below it moves — collapsed or expanded.
 */

const CARD_WIDTH = 190;
const CARD_HEIGHT = 34;
const CARD_GAP = 6;
const STACK_OFFSET = 5;
const MAX_STACK_CARDS = 3;
const GUTTER = 14;

const statusGlyph = (status?: TaskStatus | string): string => {
  switch (status) {
    case TaskStatus.COMPLETED:
      return "✓";
    case TaskStatus.FAILED:
    case TaskStatus.FAILED_WITH_TERMINAL_ERROR:
    case TaskStatus.TIMED_OUT:
      return "✕";
    case TaskStatus.CANCELED:
      return "⊘";
    case TaskStatus.COMPLETED_WITH_ERRORS:
      return "!";
    default:
      return "◷";
  }
};

const referenceOf = (task: ExecutionTask): string =>
  task.referenceTaskName || task.workflowTask?.taskReferenceName || "";

/** The task's own name reads better than the reference the server generated for it. */
const labelOf = (task: ExecutionTask): string =>
  task.workflowTask?.name || task.name || referenceOf(task);

const SideTaskCards = ({
  sideTasks,
  onSelectTask,
}: {
  sideTasks: ExecutionTask[];
  onSelectTask?: (task: ExecutionTask) => void;
}) => {
  const [expanded, setExpanded] = useState(false);
  const [hovering, setHovering] = useState(false);
  const { mode } = useContext(ColorModeContext);
  const theme = getFlowTheme(mode);
  const outlineFor = (status?: TaskStatus | string) =>
    theme.taskStatusOutline[status as TaskStatus] || "#999999";

  if (!sideTasks || sideTasks.length === 0) return null;

  const darkMode = mode === "dark";
  const surface = darkMode ? colors.gray04 : "#ffffff";
  const ink = darkMode ? colors.gray14 : undefined;

  const cardStyle = (status?: TaskStatus | string): CSSProperties => ({
    boxSizing: "border-box",
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
    borderRadius: 8,
    border: `2px dotted ${outlineFor(status)}`,
    background: surface,
    color: ink,
    display: "flex",
    alignItems: "center",
    gap: 8,
    padding: "0 10px",
    fontSize: 12,
    lineHeight: 1.2,
    boxShadow: "0 1px 3px rgba(0,0,0,0.12)",
  });

  const shell: CSSProperties = {
    position: "absolute",
    top: 0,
    left: `calc(100% + ${GUTTER}px)`,
    cursor: "pointer",
  };

  if (!expanded) {
    // A stack peeking out from the node's right edge, deepest card first.
    const stacked = Math.min(sideTasks.length, MAX_STACK_CARDS);
    const spread = hovering ? STACK_OFFSET * 2 : STACK_OFFSET;
    return (
      <div
        style={{ ...shell, width: CARD_WIDTH, height: CARD_HEIGHT }}
        onMouseEnter={() => setHovering(true)}
        onMouseLeave={() => setHovering(false)}
        onClick={(event) => {
          event.stopPropagation();
          setExpanded(true);
        }}
        title={`${sideTasks.length} task${sideTasks.length > 1 ? "s" : ""} run for this task, outside the workflow`}
      >
        {[...Array(stacked)].map((_, index) => {
          const depth = stacked - index - 1;
          const task = sideTasks[Math.min(index, sideTasks.length - 1)];
          return (
            <div
              key={`stack_${index}`}
              style={{
                ...cardStyle(task?.status),
                position: "absolute",
                top: depth * spread,
                left: depth * spread,
                transition: "top 0.2s ease-in-out, left 0.2s ease-in-out",
                opacity: depth === 0 ? 1 : 0.55,
              }}
            >
              {depth === 0 ? (
                <>
                  <span
                    style={{
                      fontWeight: 600,
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                      flex: 1,
                    }}
                  >
                    {sideTasks.length === 1
                      ? labelOf(sideTasks[0])
                      : `${sideTasks.length} side tasks`}
                  </span>
                  <span style={{ color: outlineFor(task?.status) }}>
                    {statusGlyph(task?.status)}
                  </span>
                </>
              ) : null}
            </div>
          );
        })}
      </div>
    );
  }

  return (
    <div
      style={shell}
      onClick={(event) => {
        event.stopPropagation();
        setExpanded(false);
      }}
    >
      {sideTasks.map((task, index) => (
        <div
          key={referenceOf(task) || index}
          style={{
            ...cardStyle(task.status),
            marginBottom: CARD_GAP,
          }}
          title={referenceOf(task)}
          onClick={(event) => {
            event.stopPropagation();
            onSelectTask?.(task);
          }}
        >
          <span
            style={{
              fontWeight: 600,
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
              flex: 1,
            }}
          >
            {labelOf(task)}
          </span>
          <span style={{ color: outlineFor(task.status) }}>
            {statusGlyph(task.status)}
          </span>
        </div>
      ))}
    </div>
  );
};

export default SideTaskCards;

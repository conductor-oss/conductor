import ExpandIcon from "@mui/icons-material/Expand";
import { Box, Tooltip, Typography } from "@mui/material";
import { ZoomControlsButton } from "components/ZoomControlsButton";
import _debounce from "lodash/debounce";
import _first from "lodash/first";
import _last from "lodash/last";
import { useEffect, useMemo, useRef, useState } from "react";
import Timeline from "react-vis-timeline";
import { colors } from "theme/tokens/variables";
import { ExecutionTask } from "types/Execution";
import { formatDate } from "utils";
import NoAnimRangeSlider from "./NoAnimRangeSlider";
import { processTasksToGroupsAndItems } from "./timelineUtils";

import "./timeline.scss";

type ExecutionStatusMap = Record<string, { related?: unknown }>;

const EMPTY_STATUS_MAP: ExecutionStatusMap = {};

interface VisMoment {
  format: (formatStr: string) => string;
}

interface TimelineComponentProps {
  tasks: ExecutionTask[];
  onClick: (task: { ref: string; taskId: string }) => void;
  selectedTask?: { taskId?: string } | null;
  executionStatusMap?: ExecutionStatusMap;
}

function valuetext(value: number): string {
  return formatDate(value, "dd-MM-yyyy hh:mm:ss SSS");
}

export default function TimelineComponent({
  tasks,
  onClick,
  selectedTask,
  executionStatusMap = EMPTY_STATUS_MAP,
}: TimelineComponentProps) {
  const timelineRef = useRef<Timeline>(null);

  const handleChange = (_event: Event, newValue: number | number[]) => {
    const range = newValue as number[];
    setRangeSliderValue(range);
    timelineRef.current?.timeline.setWindow(range[0], range[1], {
      animation: false,
    });
  };

  const selectedId: string | null = selectedTask?.taskId ?? null;

  const [groups, items] = useMemo(() => {
    return processTasksToGroupsAndItems(tasks, executionStatusMap);
  }, [tasks, executionStatusMap]);

  const handleClick = (e: { group: string; item: string; what: string }) => {
    const { group, item, what } = e;
    if (group && what !== "background") {
      onClick({
        ref: group,
        taskId: item,
      });
    }
  };

  const currentTime = new Date();
  const minDate: Date =
    items.length > 0 ? (_first(items)?.start ?? currentTime) : currentTime;
  const lastDate: Date =
    items.length > 0 ? (_last(items)?.end ?? currentTime) : currentTime;
  // the last item isn't necessary the latest to finish
  let lastEnd: Date = lastDate;
  items.forEach((i) => {
    if (i.end.getTime() > lastEnd.getTime()) {
      lastEnd = i.end;
    }
  });

  const diffMilli = lastEnd.getTime() - minDate.getTime();
  // Less than 100ms has odd behaviour with the Timeline component and needs more buffer
  const endBuffer = diffMilli * (diffMilli < 100 ? 0.06 : 0.01);
  const maxDate = new Date(lastEnd.getTime() + endBuffer);

  const onFit = () => {
    timelineRef.current?.timeline.fit();
    setRangeSliderValue([minDate.getTime(), maxDate.getTime()]);
  };

  const [rangeSliderValue, setRangeSliderValue] = useState([
    minDate.getTime(),
    maxDate.getTime(),
  ]);

  const debouncedSetRangeSliderValue = useRef(
    _debounce((e: { start: Date; end: Date; byUser: boolean }) => {
      if (e.byUser) {
        setRangeSliderValue([e.start.getTime(), e.end.getTime()]);
      }
    }, 100),
  );

  if (timelineRef.current?.timeline) {
    timelineRef.current.timeline.off(
      "rangechanged",
      debouncedSetRangeSliderValue.current,
    );
    timelineRef.current.timeline.on(
      "rangechanged",
      debouncedSetRangeSliderValue.current,
    );
  }

  useEffect(() => {
    if (!timelineRef.current?.timeline) return;
    timelineRef.current.timeline.setItems(items);
    timelineRef.current.timeline.setGroups(groups);
  }, [groups, items]);

  return (
    <Box
      id="execution-timeline-wrapper"
      sx={{ minHeight: 450, overflowY: "auto", overflowX: "hidden" }}
    >
      <Box
        sx={{
          position: "absolute",
          top: "30px",
          left: "30px",
          borderRadius: "6px",
          boxShadow: "0px 4px 12px 0px #0000001F",
          backgroundColor: colors.white,
          display: "flex",
          userSelect: "none",
          zIndex: 100,
        }}
      >
        {maxDate > minDate && (
          <Tooltip title="Zoom to Fit">
            <ZoomControlsButton id="fit-screen-button" onClick={onFit}>
              <ExpandIcon
                sx={{
                  transform: "rotate(90deg)",
                  color: colors.greyText,
                }}
              />
            </ZoomControlsButton>
          </Tooltip>
        )}
      </Box>

      <Box
        className="timeline-container"
        style={{ maxHeight: "calc(100vh - 270px)", overflowY: "auto" }}
        sx={{
          "&.vis-labelset, .vis-labelset .vis-label, .vis-time-axis .vis-text":
            {
              color: (theme) =>
                theme.palette?.mode === "dark" ? colors.gray14 : undefined,
            },
        }}
      >
        <Timeline
          ref={timelineRef}
          selection={selectedId != null ? [selectedId] : []}
          clickHandler={handleClick}
          options={{
            orientation: "top",
            zoomKey: "ctrlKey",
            type: "range",
            stack: false,
            min: minDate,
            max: maxDate,
            format: {
              // vis-timeline passes a moment-like object at runtime despite the Date type
              majorLabels: function (date: Date) {
                const d = date as unknown as VisMoment;
                return d.format("DD-MM-YYYY") + " " + d.format("hh:mm::ss");
              },
            },
          }}
        />
      </Box>
      <Box
        sx={{
          paddingX: { xs: 2, sm: 8 },
          paddingY: 2,
          display: "flex",
          justifyContent: "center",
        }}
      >
        <Box
          sx={{
            borderRadius: "6px",
            boxShadow: "0px 4px 12px 0px #0000001F",
            backgroundColor: colors.white,
            padding: { xs: "8px 12px", sm: "8px 16px" },
            userSelect: "none",
            display: "flex",
            alignItems: "center",
            gap: "16px",
          }}
        >
          <Box
            sx={{
              display: "flex",
              flexDirection: "row",
              flexWrap: "wrap",
              gap: { xs: "12px", sm: "16px" },
              alignItems: "center",
              width: "100%",
            }}
          >
            <Box sx={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <Box
                sx={{
                  width: "16px",
                  height: "16px",
                  backgroundColor: "#ffb74d",
                  opacity: 0.7,
                  borderRadius: "2px",
                  flexShrink: 0,
                }}
              />
              <Typography
                variant="caption"
                sx={{
                  fontSize: "12px",
                  color: colors.greyText,
                  whiteSpace: { xs: "normal", sm: "nowrap" },
                }}
              >
                Task Queue Time
              </Typography>
            </Box>
            <Box sx={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <Box
                sx={{
                  width: "16px",
                  height: "16px",
                  backgroundColor: "#9fdcaa",
                  borderRadius: "2px",
                  flexShrink: 0,
                }}
              />
              <Typography
                variant="caption"
                sx={{
                  fontSize: "12px",
                  color: colors.greyText,
                  whiteSpace: { xs: "normal", sm: "nowrap" },
                }}
              >
                Task Execution Duration
              </Typography>
            </Box>
            <Box sx={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <Box
                sx={{
                  display: "flex",
                  alignItems: "center",
                  gap: "2px",
                  flexShrink: 0,
                }}
              >
                <Box
                  sx={{
                    width: "6px",
                    height: "16px",
                    backgroundColor: "#9fdcaa",
                    borderRadius: "2px 0 0 2px",
                  }}
                />
                <Box
                  sx={{
                    width: "4px",
                    height: "1px",
                    backgroundColor: "#999",
                  }}
                />
                <Box
                  sx={{
                    width: "6px",
                    height: "16px",
                    backgroundColor: "#9fdcaa",
                    borderRadius: "0 2px 2px 0",
                  }}
                />
              </Box>
              <Typography
                variant="caption"
                sx={{
                  fontSize: "12px",
                  color: colors.greyText,
                  whiteSpace: { xs: "normal", sm: "nowrap" },
                }}
              >
                Conductor Latency (Gap)
              </Typography>
            </Box>
          </Box>
        </Box>
      </Box>
      {maxDate > minDate && (
        <Box
          id="timeline-slider-wrapper"
          sx={{
            paddingX: 8,
          }}
        >
          <Box>Adjust visible range</Box>
          <NoAnimRangeSlider
            getAriaLabel={() => "Temperature range"}
            value={rangeSliderValue}
            min={minDate.getTime()}
            max={maxDate.getTime()}
            onChange={handleChange}
            valueLabelDisplay="auto"
            valueLabelFormat={valuetext}
          />
        </Box>
      )}
    </Box>
  );
}

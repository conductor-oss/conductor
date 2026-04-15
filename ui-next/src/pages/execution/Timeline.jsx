import ExpandIcon from "@mui/icons-material/Expand";
import { Box, Tooltip, Typography } from "@mui/material";
import _debounce from "lodash/debounce";
import _first from "lodash/first";
import _flow from "lodash/flow";
import _identity from "lodash/identity";
import _last from "lodash/last";
import { useEffect, useMemo, useRef, useState } from "react";
import Timeline from "react-vis-timeline";
import { ZoomControlsButton } from "components/ZoomControlsButton";
import { colors } from "theme/tokens/variables";
import { formatDate } from "utils";
import NoAnimRangeSlider from "./NoAnimRangeSlider";
import { processTasksToGroupsAndItems } from "./timelineUtils";

import "./timeline.scss";

function valuetext(value) {
  const valueText = formatDate(value, "dd-MM-yyyy hh:mm:ss SSS");
  return valueText;
}

export default function TimelineComponent({
  tasks,
  onClick,
  selectedTask,
  executionStatusMap,
}) {
  const timelineRef = useRef();

  const handleChange = (event, newValue) => {
    setRangeSliderValue(newValue);
    timelineRef.current.timeline.setWindow(newValue[0], newValue[1], {
      animation: false,
    });
  };

  let selectedId = null;
  if (selectedTask) {
    selectedId = selectedTask.taskId;
  }

  const [groups, items] = useMemo(() => {
    return processTasksToGroupsAndItems(tasks, executionStatusMap);
  }, [tasks, executionStatusMap]);

  const handleClick = (e) => {
    const { group, item, what } = e;
    if (group && what !== "background") {
      onClick({
        ref: group,
        taskId: item,
      });
    }
  };

  const currentTime = new Date();
  const minDate = items.length > 0 ? _first(items)?.start : currentTime;
  const lastDate = items.length > 0 ? _last(items)?.end : currentTime;
  // the last item isn't necessary the latest to finish
  let lastEnd = lastDate;
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
    timelineRef.current.timeline.fit();
    setRangeSliderValue([minDate.getTime(), maxDate.getTime()]);
  };

  const [rangeSliderValue, setRangeSliderValue] = useState([
    minDate.getTime(),
    maxDate.getTime(),
  ]);

  const debouncedSetRangeSliderValue = useRef(
    _debounce((e) => {
      if (e.byUser) {
        setRangeSliderValue([e.start.getTime(), e.end.getTime()]);
      }
    }, 100),
    [setRangeSliderValue],
  );

  if (timelineRef.current) {
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
                color={colors.greyText}
                sx={{
                  transform: "rotate(90deg)",
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
          selection={selectedId}
          clickHandler={handleClick}
          options={{
            orientation: "top",
            zoomKey: "ctrlKey",
            type: "range",
            stack: false,
            min: minDate,
            max: maxDate,
            format: {
              majorLabels: function (date) {
                return (
                  date.format("DD-MM-YYYY") + " " + date.format("hh:mm::ss")
                );
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

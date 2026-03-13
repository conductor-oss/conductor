import { Box } from "@mui/material";
import MuiTypography from "components/MuiTypography";
import { useState } from "react";
import { ConductorAutoComplete } from "./ConductorAutoComplete";

const EVENT_COLORS = [
  "#4FAAD1",
  "#6569AC",
  "#45AC59",
  "#C99E00",
  "#EE6B31",
  "#CE2836",
];

const checkForWarningMessages = (
  labels: string[],
  suggestions: string[],
  value: string,
) => {
  if (!value) {
    return "";
  }
  const valueParts = value.split(":");

  if (valueParts.length > labels.length) {
    return "Warning: The event should not contain more than three parts separated by colons";
  }

  // Check if first term exists in the suggestions
  const firstTermExists = suggestions.some(
    (suggestion) => suggestion.split(":")[0] === valueParts[0],
  );

  // Check if second term exists in the suggestions
  const secondTermExists = suggestions.some(
    (suggestion) => suggestion.split(":")[1] === valueParts[1],
  );

  if (valueParts[0] && !firstTermExists)
    return `Warning: ${valueParts[0]} is not a valid ${labels[0]}`;
  if (valueParts[1] && !secondTermExists)
    return `Warning: ${valueParts[1]} is not a valid ${labels[1]}`;
};

const getItems = (labels: string[], value: string) => {
  const valueParts = value?.split(":");

  const items = labels?.map((label, index) => ({
    label,
    value: valueParts?.[index] || "",
  }));

  return items;
};

export interface EventExpressionHelpProps {
  labels: string[];
  suggestions: string[];
  width: number;
  height: number;
  value: string;
  onChange: (value: string) => void;
}

export const EventExpressionHelp = ({
  labels,
  suggestions,
  width,
  height,
  value = "",
  onChange = () => {},
}: EventExpressionHelpProps) => {
  const [data, setData] = useState({
    warning: checkForWarningMessages(labels, suggestions, value),
    items: getItems(labels, value),
  });
  const [highlightedPart, setHighlightedPart] = useState<number | null>(null);

  const handleEventChange = (val: string) => {
    onChange(val);
    const updatedItems = getItems(labels, val);
    const updatedWarning = checkForWarningMessages(labels, suggestions, val);
    setData((prevState) => ({
      ...prevState,
      items: updatedItems,
      warning: updatedWarning,
    }));
  };

  const getHighlightedPart = (value: string, selectionStart: number) => {
    const partsUntilCursor = value.substring(0, selectionStart).split(":");
    setHighlightedPart(partsUntilCursor.length - 1);
  };

  return (
    <Box>
      <Box padding={6}>
        <MuiTypography marginBottom="8px" opacity={0.5}>
          EVENT EXPRESSIONS HELP
        </MuiTypography>
        <Box sx={{ display: "flex", padding: "12px 12px 12px 5px" }}>
          <Box>
            <Box
              sx={{
                position: "relative",
                height: `${height}px`,
                width: `${width}px`,
              }}
            >
              {data?.items.map((_, index) => {
                const xStep = width / data?.items.length;
                const yStep = height / data?.items.length;
                const colorIndex = index % EVENT_COLORS.length;
                return (
                  <Box
                    key={index}
                    sx={{
                      position: "absolute",
                      top: `${index * yStep}px`,
                      left: `${index * xStep}px`,
                      width: `${width - index * xStep}px`,
                      height: `${height - index * yStep}px`,
                      borderRadius: "14px 0 0 0",
                      borderTop: `2px solid ${EVENT_COLORS[colorIndex]}`,
                      borderLeft: `2px solid ${EVENT_COLORS[colorIndex]}`,
                      opacity:
                        highlightedPart === index || highlightedPart === null
                          ? 1
                          : 0.5,
                    }}
                  ></Box>
                );
              })}
            </Box>
            <Box
              sx={{
                display: "flex",
                marginLeft: "-4px",
              }}
            >
              {data?.items.map((_, index) => {
                const blockWidth = width / data?.items.length;
                const colorIndex = index % EVENT_COLORS.length;
                return (
                  <Box
                    key={index}
                    sx={{
                      width: `${blockWidth}px`,
                      fontSize: "18px",
                      fontWeight: "bold",
                      textAlign: "left",
                      color: EVENT_COLORS[colorIndex],
                      opacity:
                        highlightedPart === index || highlightedPart === null
                          ? 1
                          : 0.5,
                    }}
                  >
                    *
                  </Box>
                );
              })}
            </Box>
          </Box>
          <Box>
            <Box
              sx={{
                display: "flex",
                flexDirection: "column",
                marginLeft: "6px",
                marginTop: "-7px",
              }}
            >
              {data?.items.map((item, index) => {
                const blockHeight = height / data?.items.length;
                const colorIndex = index % EVENT_COLORS.length;
                return (
                  <Box
                    key={index}
                    sx={{
                      height: `${blockHeight}px`,
                      fontSize: "12px",
                      textAlign: "left",
                      whiteSpace: "nowrap",
                      textOverflow: "ellipsis",
                      overflow: "hidden",
                      color: EVENT_COLORS[colorIndex],
                      fontWeight: 400,
                      lineHeight: "18px",
                      opacity:
                        highlightedPart === index || highlightedPart === null
                          ? 1
                          : 0.5,
                    }}
                  >
                    {item.label}: {item.value}
                  </Box>
                );
              })}
            </Box>
          </Box>
        </Box>
      </Box>
      <ConductorAutoComplete
        label="Event"
        fullWidth
        required
        placeholder="Event String"
        id="event-string-input"
        options={suggestions}
        value={value}
        onChange={(_, val: any) => handleEventChange(val)}
        onInputChange={(_, val) => handleEventChange(val)}
        freeSolo
        selectOnFocus
        onBlur={(_e) => setHighlightedPart(null)}
        onKeyDown={(e: any) =>
          getHighlightedPart(e.target.value, e.target.selectionStart)
        }
        onKeyUp={(e: any) =>
          getHighlightedPart(e.target.value, e.target.selectionStart)
        }
        helperText={data?.warning}
        error={data?.warning ? true : false}
      />
    </Box>
  );
};

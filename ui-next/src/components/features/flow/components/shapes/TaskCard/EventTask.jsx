import { useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";

const EventTask = ({ nodeData }) => {
  const { mode } = useContext(ColorModeContext);
  const darkMode = mode === "dark";

  const { task } = nodeData;
  const { sink } = task;

  const prefix = sink?.split(":")[0];
  const value = sink?.split(":")[1];

  return (
    <div style={{ marginTop: "20px" }}>
      <div style={{ display: "flex", alignItems: "center", width: "100%" }}>
        {prefix ? (
          <div
            style={{
              fontSize: "0.8em",
              padding: "4px 8px",
              color: darkMode ? colors.gray14 : colors.gray01,
              background: darkMode ? colors.gray06 : colors.gray12,
              borderRadius: "5px",
              height: "fit-content",
            }}
          >
            {prefix}
          </div>
        ) : null}
        <div
          style={{
            padding: "0 8px",
            lineHeight: "2em",
            overflow: "hidden",
            textOverflow: "ellipsis",
            wordBreak: "keep-all",
            whiteSpace: "nowrap",
          }}
        >
          {value ? value : "No Value"}
        </div>
      </div>
    </div>
  );
};

export default EventTask;

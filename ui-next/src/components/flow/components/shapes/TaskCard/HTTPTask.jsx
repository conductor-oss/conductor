import { Link } from "@mui/material";
import { Link as LinkIcon } from "@phosphor-icons/react";
import { useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import { isValidUri } from "./helpers";

const HTTPTask = ({ nodeData }) => {
  const { mode } = useContext(ColorModeContext);
  const darkMode = mode === "dark";

  const { task } = nodeData;
  const {
    inputParameters: { http_request: request },
  } = task;

  const method = request?.method
    ? request?.method
    : task?.inputParameters?.method;

  const uri = request?.uri ? request?.uri : task?.inputParameters?.uri;

  const isClickableUri = method === "GET" && isValidUri(uri);

  return (
    <div style={{ marginTop: "20px" }}>
      <div style={{ display: "flex", alignItems: "center", width: "100%" }}>
        <LinkIcon style={{ marginRight: "10px", flexShrink: 0 }} />
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
          {method}
        </div>
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
          {isClickableUri ? (
            <Link href={uri} target="_blank" rel="noreferrer">
              {uri}
            </Link>
          ) : (
            uri
          )}
        </div>
      </div>
    </div>
  );
};

export default HTTPTask;

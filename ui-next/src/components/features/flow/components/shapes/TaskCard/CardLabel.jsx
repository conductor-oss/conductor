import { TaskType } from "types";
import theme from "../../../theme";

const shortenedTypeTag = {
  FORK_JOIN_COLLAPSED: "DYN. CHILDREN",
  [TaskType.JSON_JQ_TRANSFORM]: "JSON JQ",
  [TaskType.EXCLUSIVE_JOIN]: "EX. JOIN",
  [TaskType.FORK_JOIN]: "FORK JOIN",
  [TaskType.FORK_JOIN_DYNAMIC]: "DYN. FORK",
  [TaskType.INLINE]: "INLINE",
  [TaskType.KAFKA_PUBLISH]: "KAFKA",
  [TaskType.SIMPLE]: "SIMPLE",
};

const CardLabel = ({
  type,
  displayDescription = false,
  integrationIconName,
}) => (
  <div>
    <div
      style={{
        position: "absolute",
        top: "0px",
        right: "0px",
        height: "fit-content",
        padding: "4px 8px",
        fontSize: "0.8em",
        background: displayDescription
          ? "transparent"
          : theme.taskCard.cardLabel.background,
        color: displayDescription
          ? "transparent"
          : theme.taskCard.cardLabel.color,
        borderRadius: "5px",
        marginLeft: "8px",
        transition: displayDescription ? "all 0.3s ease-in-out" : "none",
      }}
    >
      {type !== TaskType.MCP
        ? shortenedTypeTag[type] || type
        : integrationIconName?.toUpperCase() || "MCP"}
    </div>
  </div>
);
export default CardLabel;

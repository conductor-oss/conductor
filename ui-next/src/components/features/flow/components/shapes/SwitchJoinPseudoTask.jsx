import { taskToSize } from "components/features/flow/nodes/mapper/layout";
import { gray13, lightShadesGray } from "theme/tokens/colors";

const SwitchJoin = ({ nodeData }) => {
  const { task } = nodeData;
  const terminalClick = (event) => {
    event.stopPropagation();
  };

  const { width, height } = taskToSize(task);
  return (
    <div
      onClick={terminalClick}
      style={{
        width: `${width}px`,
        height: height - 10,
        boxShadow: "none",
        border: "1px dashed black",
        backgroundColor: gray13,
        borderRadius: "20px",
        display: "flex",
        color: lightShadesGray,
        textAlign: "center",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 14,
        fontWeight: 500,
        gap: "10px",
        padding: "0 20px",
        whiteSpace: "nowrap",
      }}
    >
      {`// Marks end of switch`}
      <div
        style={{
          color: lightShadesGray,
          display: "block",
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
          fontWeight: "bold",
        }}
      >
        {task?.taskReferenceName}
      </div>
    </div>
  );
};

export default SwitchJoin;

import { CircularProgress } from "@mui/material";
import { Check, Prohibit } from "@phosphor-icons/react";
import { TaskStatus } from "types";

/** Small status badge (20×20 instead of CardStatusBadge's 30×30). */
export function NodeStatusBadge({ status }: { status: TaskStatus }) {
  const size = 20;
  const half = size / 2;
  if (status === TaskStatus.IN_PROGRESS) {
    return (
      <div
        style={{
          position: "absolute",
          top: -half,
          right: -half,
          width: size,
          height: size,
          zIndex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <CircularProgress size={size} thickness={3} sx={{ color: "#f59e0b" }} />
        <div
          style={{
            position: "absolute",
            width: 6,
            height: 6,
            borderRadius: "50%",
            backgroundColor: "#f59e0b",
          }}
        />
      </div>
    );
  }
  if (status !== TaskStatus.COMPLETED && status !== TaskStatus.FAILED)
    return null;
  const bg = status === TaskStatus.COMPLETED ? "#40BA56" : "#DD2222";
  return (
    <div
      style={{
        position: "absolute",
        top: -half,
        right: -half,
        width: size,
        height: size,
        borderRadius: "50%",
        backgroundColor: bg,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        boxShadow: "0 0 4px rgba(0,0,0,0.4)",
        zIndex: 1,
      }}
    >
      {status === TaskStatus.COMPLETED ? (
        <Check size={11} color="white" weight="bold" />
      ) : (
        <Prohibit size={11} color="white" />
      )}
    </div>
  );
}

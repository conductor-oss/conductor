import { Chip, Box } from "@mui/material";
import DomainIcon from "./icons/Buildings";
import _isNil from "lodash/isNil";

const statusToColor = (status) => {
  switch (status) {
    case "COMPLETED":
      return "secondary";
    case "FAILED":
      return "error";
    default:
      return undefined;
  }
};

export const SimpleTask = ({ nodeData }) => {
  const { task } = nodeData;

  return _isNil(task?.executionData?.domain) ? null : (
    <Box mt={1}>
      <Chip
        label={task.executionData.domain}
        color={statusToColor(task.executionData.status)}
        style={{ padding: 2 }}
        avatar={<DomainIcon size={14} color={"white"} />}
      />
    </Box>
  );
};

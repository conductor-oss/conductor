import NavLink from "./NavLink";
import rison from "rison";

export default function ({ taskId, workflowId }) {
  const taskObj = rison.encode({
    id: taskId,
  });
  return (
    <NavLink path={`/execution/${workflowId}?task=${taskObj}`}>
      {taskId}
    </NavLink>
  );
}

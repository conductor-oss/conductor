import Button from "components/ui/buttons/MuiButton";
import { WorkflowEditContext } from "pages/definition/state";
import {
  TaskAndCrumbs,
  usePerformOperationOnDefinition,
} from "pages/definition/state/usePerformOperationOnDefintion";
import { MouseEvent, ReactNode, useContext } from "react";
import ForkIcon from "./icons/ForkIcon";

interface AddPathButtonProps {
  children: ReactNode;
  nodeData: TaskAndCrumbs;
}

const AddPathButton = ({ children, nodeData }: AddPathButtonProps) => {
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const { handleAddSwitchPath: onAddSwitchPath } =
    usePerformOperationOnDefinition(workflowDefinitionActor!);

  const handleAddEdge = (e: MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation();
    onAddSwitchPath(nodeData);
  };

  return (
    <Button
      size="small"
      startIcon={<ForkIcon size={14} />}
      className="AddEdgeButton"
      color="tertiary"
      onClick={handleAddEdge}
    >
      {children}
    </Button>
  );
};

export default AddPathButton;

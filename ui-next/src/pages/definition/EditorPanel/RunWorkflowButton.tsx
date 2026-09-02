import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "../state/types";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import { UnderlinedText } from "components/ui/UnderlinedText";

export interface RunWorkflowButtonProps {
  definitionActor: ActorRef<WorkflowDefinitionEvents>;
  disabled: boolean;
}

export const RunWorkflowButton: FunctionComponent<RunWorkflowButtonProps> = ({
  definitionActor: service,
  disabled,
}) => {
  const executeWorkflow = () => {
    service.send({ type: DefinitionMachineEventTypes.HANDLE_SAVE_AND_RUN });
  };

  const executeWorkflowWithInputs = () => {
    service.send({ type: DefinitionMachineEventTypes.HANDLE_RUN_WITH_INPUTS });
  };

  const options = [
    {
      label: "Execute with inputs…",
      id: "run-with-inputs-btn",
      onClick: executeWorkflowWithInputs,
    },
  ];

  return (
    <SplitButton
      id="head-action-run-btn"
      startIcon={<RocketLaunchIcon />}
      tooltip="Run workflow (Ctrl E)"
      options={options}
      primaryOnClick={executeWorkflow}
      disabled={disabled}
      data-testid="workflow-definition-run-button"
    >
      <UnderlinedText text="Execute" underlinedIndexes={[0]} />
    </SplitButton>
  );
};

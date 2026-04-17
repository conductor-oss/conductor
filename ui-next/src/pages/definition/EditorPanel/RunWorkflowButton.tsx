import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "../state/types";
import { ButtonTooltip } from "components/ui/buttons/ButtonTooltip";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
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
  return (
    <ButtonTooltip
      id="head-action-run-btn"
      variant="contained"
      tooltip="Run workflow (Ctrl E)"
      onClick={executeWorkflow}
      startIcon={<RocketLaunchIcon />}
      children={<UnderlinedText text="Execute" underlinedIndexes={[0]} />}
      disabled={disabled}
    />
  );
};

import { useSelector } from "@xstate/react";
import { ActorRef } from "xstate";

import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "pages/definition/state/types";

export const usePanelChanges = (actor: ActorRef<WorkflowDefinitionEvents>) => {
  const setLeftPanelExpanded = () => {
    actor.send({
      type: DefinitionMachineEventTypes.HANDLE_LEFT_PANEL_EXPANDED,
      onSelectNode: false,
    });
  };

  const leftPanelExpanded = useSelector(actor, (state) =>
    state.matches("ready.rightPanel.closed"),
  );

  return { leftPanelExpanded, setLeftPanelExpanded };
};

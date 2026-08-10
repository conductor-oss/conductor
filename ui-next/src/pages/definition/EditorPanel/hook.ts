import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";

import fastDeepEqual from "fast-deep-equal";

import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "../state/types";
import { usePanelChanges } from "pages/definition/state/usePanelChanges";
import {
  isSaveRequestSelector,
  versionSelector,
  versionsSelector,
} from "./selectors";

export const useDefinitionMachine = (
  service: ActorRef<WorkflowDefinitionEvents>,
) => {
  const handleConfirmReset = () =>
    service.send({ type: DefinitionMachineEventTypes.RESET_CONFIRM_EVT });

  const handleChangeVersion = (version: string) =>
    service.send({
      type: DefinitionMachineEventTypes.CHANGE_VERSION_EVT,
      version,
    });

  const handleConfirmDelete = () =>
    service.send({ type: DefinitionMachineEventTypes.DELETE_CONFIRM_EVT });

  const handleCancelRequest = () =>
    service.send({ type: DefinitionMachineEventTypes.CANCEL_EVENT_EVT });

  const handleConfirmLastForkRemovalRequest = () =>
    service.send({
      type: DefinitionMachineEventTypes.CONFIRM_LAST_FORK_REMOVAL,
    });

  const isConfirmDelete = useSelector(service, (state) =>
    state.matches("ready.rightPanel.opened.confirmDelete"),
  );

  const version = useSelector(service, versionSelector);

  const versions = useSelector(service, versionsSelector, fastDeepEqual);

  const isConfirmReset = useSelector(service, (state) =>
    state.matches("ready.rightPanel.opened.confirmReset"),
  );

  const isSaveRequest = useSelector(service, isSaveRequestSelector);

  const isRunWorkflow = useSelector(service, (state) =>
    state.matches("ready.rightPanel.opened.runWorkflow"),
  );

  const isConfirmingForkRemoval = useSelector(service, (state) =>
    state.matches("ready.diagram.branchRemoval.confirmForkJoinRemoval"),
  );

  const openedTab = useSelector(service, (state) => state.context.openedTab);

  const changeTab = (tab: number) => {
    service.send({ type: DefinitionMachineEventTypes.CHANGE_TAB_EVT, tab });
  };

  const { leftPanelExpanded, setLeftPanelExpanded } = usePanelChanges(service);

  return [
    {
      handleConfirmReset,
      handleChangeVersion,
      handleConfirmDelete,
      handleCancelRequest,
      handleConfirmLastForkRemovalRequest,
      changeTab,
      setLeftPanelExpanded,
    },
    {
      isConfirmDelete,
      version,
      versions,
      isConfirmReset,
      openedTab,
      isSaveRequest,
      isConfirmingForkRemoval,
      leftPanelExpanded,
      isRunWorkflow,
    },
  ] as const;
};

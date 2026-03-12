import { useSelector } from "@xstate/react";
import { ActorRef } from "xstate";

import {
  IntegrationDrillDownMenuProp,
  IntegrationMenuItem,
  MainStates,
  RichAddTaskMenuEvents,
  RichAddTaskMenuEventTypes,
} from "./types";
import { NodeData } from "reaflow";

export const useRichAddTaskMenu = (
  richAddTaskMenuActor: ActorRef<RichAddTaskMenuEvents>,
) => {
  const menuType = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.menuType,
  );

  const supportedIntegrations = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.supportedIntegrations,
  );

  const availableIntegrations = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.availableIntegrations,
  );

  const integrationDefs = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.integrationDefs,
  );

  const integrationTypes = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.integrationTypes,
  );

  const integrationDrillDownMenu = useSelector(
    richAddTaskMenuActor,
    (state) => state.context?.integrationDrillDownMenu,
  );

  // Add scroll position tracking
  const scrollPosition = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.toScrollTop,
  );

  const operationContext = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.operationContext,
  );

  const nodes = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.nodes,
  ) as NodeData[];

  const workerMenuItems = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.workerMenuItems ?? [],
  );

  const subWorkflowMenuItems = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.workflowMenuItems ?? [],
  );

  const selectedTab = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.selectedTab,
  );

  const isFetching = useSelector(
    richAddTaskMenuActor,
    (state) =>
      state.matches(`init.main.${MainStates.FETCH_FOR_TASK_DEFINITIONS}`) ||
      state.matches(`init.main.${MainStates.FETCH_FOR_WORKFLOW_DEFINITIONS}`) ||
      state.matches(`init.main.${MainStates.FETCH_FOR_INTEGRATIONS}`),
  );

  const isFetchingIntegrationTools = useSelector(
    richAddTaskMenuActor,
    (state) =>
      state.matches(`init.main.${MainStates.FETCH_FOR_INTEGRATION_TOOLS}`),
  );

  const searchQuery = useSelector(
    richAddTaskMenuActor,
    (state) => state.context.searchQuery,
  );

  const handleChangeMenuType = (menuType: "quick" | "advanced") => {
    richAddTaskMenuActor.send({
      type: RichAddTaskMenuEventTypes.SET_MENU_TYPE,
      menuType,
    });
  };

  const handleFetchIntegrationTools = (integration: IntegrationMenuItem) => {
    richAddTaskMenuActor.send({
      type: RichAddTaskMenuEventTypes.FETCH_INTEGRATION_TOOLS,
      integration,
    });
  };

  const refetchIntegrations = () => {
    richAddTaskMenuActor.send({
      type: RichAddTaskMenuEventTypes.REFETCH_INTEGRATIONS,
    });
  };

  const handleUpdateIntegrationDrillDown = (
    integration: IntegrationDrillDownMenuProp,
  ) => {
    richAddTaskMenuActor.send({
      type: RichAddTaskMenuEventTypes.UPDATE_INTEGRATION_DRILL_DOWN,
      data: integration,
    });
  };

  const handleTyping = (value: any) => {
    richAddTaskMenuActor.send({
      type: RichAddTaskMenuEventTypes.TYPING,
      text: value,
    });
  };

  return [
    {
      menuType,
      supportedIntegrations: supportedIntegrations ?? [],
      availableIntegrations: availableIntegrations ?? [],
      integrationDefs: integrationDefs ?? [],
      integrationTypes: integrationTypes ?? [],
      integrationDrillDownMenu: integrationDrillDownMenu ?? {},
      scrollPosition,
      operationContext,
      nodes,
      workerMenuItems,
      subWorkflowMenuItems,
      selectedTab,
      isFetching,
      isFetchingIntegrationTools,
      searchQuery,
    },
    {
      handleChangeMenuType,
      handleFetchIntegrationTools,
      refetchIntegrations,
      handleUpdateIntegrationDrillDown,
      handleTyping,
    },
  ] as const;
};

import {
  RichAddMenuTabs,
  RichAddTaskMenuMachineContext,
  SetSelectedTabEvent,
} from "./types";

export const isTabIsWorkers = (
  _context: RichAddTaskMenuMachineContext,
  { tab }: SetSelectedTabEvent,
) => {
  return tab === RichAddMenuTabs.WORKERS_TAB;
};

export const isTabIsSubWorkflows = (
  _context: RichAddTaskMenuMachineContext,
  { tab }: SetSelectedTabEvent,
) => tab === RichAddMenuTabs.SUB_WORKFLOWS_TAB;

export const isTaskDefNotFetched = ({
  isTaskDefFetched,
}: RichAddTaskMenuMachineContext) => !isTaskDefFetched;

export const isSubWfNotFetched = ({
  isSubWfFetched,
}: RichAddTaskMenuMachineContext) => !isSubWfFetched;

export const isIntegrationsNotFetched = ({
  isIntegrationsFetched,
}: RichAddTaskMenuMachineContext) => !isIntegrationsFetched;

export const isTabIsIntegrations = (
  _context: RichAddTaskMenuMachineContext,
  { tab }: SetSelectedTabEvent,
) => tab === RichAddMenuTabs.INTEGRATIONS_TAB;

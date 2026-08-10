import { createMachine } from "xstate";
import {
  SearchActionTypes,
  SearchMachineContext,
  SearchMachineStates,
} from "./types";

import * as services from "./services";
import * as actions from "./actions";

export const searchMachine = createMachine<SearchMachineContext>(
  {
    id: "searchMachine",
    initial: SearchMachineStates.INIT,
    predictableActionArguments: true,
    context: {
      authHeaders: undefined,
      // Core OSS searchable data
      taskDefinitions: [],
      workflowDefinitions: [],
      schedulers: [],
      events: [],
      // Plugin-contributed data (populated via hook, not machine)
      pluginData: {},
      searchTerm: "",
      maxSearchResults: undefined,
    },
    states: {
      [SearchMachineStates.INIT]: {
        type: "parallel",
        states: {
          [SearchMachineStates.FETCHER]: {
            type: "parallel",
            states: {
              // Core OSS fetchers only
              [SearchMachineStates.FETCH_TASK_DEFINITIONS]: {
                invoke: {
                  src: "fetchForTaskNames",
                  onDone: {
                    actions: ["persistTaskNames"],
                  },
                  onError: {
                    actions: ["persistErrorMessage"],
                  },
                },
              },
              [SearchMachineStates.FETCH_WF_DEFINITIONS]: {
                invoke: {
                  src: "fetchForWorkflowDef",
                  onDone: {
                    actions: ["persistWorkflowNames"],
                  },
                  onError: {
                    actions: ["persistErrorMessage"],
                  },
                },
              },
              [SearchMachineStates.FETCH_SCHEDULERS]: {
                invoke: {
                  src: "fetchForScheduleNames",
                  onDone: {
                    actions: ["persistScheduleNames"],
                  },
                  onError: {
                    actions: ["persistErrorMessage"],
                  },
                },
              },
              [SearchMachineStates.FETCH_EVENTS]: {
                invoke: {
                  src: "fetchForEventNames",
                  onDone: {
                    actions: ["persistEventNames"],
                  },
                  onError: {
                    actions: ["persistErrorMessage"],
                  },
                },
              },
            },
          },
          [SearchMachineStates.FILTER]: {
            initial: SearchMachineStates.WAIT,
            states: {
              [SearchMachineStates.WAIT]: {
                after: {
                  200: {
                    target: SearchMachineStates.FILTERING,
                  },
                },
              },
              [SearchMachineStates.FILTERING]: {
                on: {
                  [SearchActionTypes.UPDATE_SEARCH_TERM]: {
                    actions: ["persistSearchTerm"],
                  },
                },
              },
            },
          },
        },
      },
    },
  },
  {
    services: services as any,
    actions: actions as any,
  },
);

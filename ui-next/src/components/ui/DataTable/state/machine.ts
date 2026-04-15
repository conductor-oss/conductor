import { createMachine } from "xstate";
import * as actions from "./actions";
import * as services from "./services";
import * as guards from "./guards";
import {
  DataTableMachineContext,
  DataTableEventTypes,
  DataTableEvents,
} from "./types";

export const dataTableMachine = createMachine<
  DataTableMachineContext,
  DataTableEvents
>(
  {
    id: "dataTableMachine",
    predictableActionArguments: true,
    initial: "init",
    context: {
      columnOrderAndVisibility: [],
      localStorageKey: undefined,
      searchTerm: "",
    },
    on: {
      // [DataTableEventTypes.SET_DATA]: {
      //   actions: ["persistData"],
      // },
      [DataTableEventTypes.SET_SEARCH_TERM]: {
        actions: ["persistSearchTerm"],
      },
      [DataTableEventTypes.SET_FILTER_OBJ]: {
        actions: ["persistFilterObj"],
      },
    },
    states: {
      init: {
        always: [
          {
            cond: "noLocalStorageKey",
            target: "renderedTable",
          },
          { target: "takeLocalStorageConfigurations" },
        ],
      },
      renderedTable: {
        on: {
          [DataTableEventTypes.SET_ORDER_AND_VISIBILITY]: {
            actions: ["persistOrderAndVisibility"],
          },
        },
      },
      renderedTableStorageSupport: {
        on: {
          [DataTableEventTypes.SET_ORDER_AND_VISIBILITY]: {
            actions: ["persistOrderAndVisibility"],
            target: "persisOrderToLocalStorage",
          },
        },
      },
      useLocalStorageOrderAndVisibility: {
        entry: "persistOrderAndVisibility",
        always: "renderedTableStorageSupport",
      },
      persisOrderToLocalStorage: {
        invoke: {
          src: "saveOrderAndVisibility",
          id: "localStoragePersistOrderAndVisibility",
          onDone: {
            target: "renderedTableStorageSupport",
          },
        },
      },
      takeLocalStorageConfigurations: {
        invoke: {
          src: "maybePullOrderAndVisibility",
          id: "localStoragePull",
          onDone: [
            {
              cond: "isLocalStorageContentTrusted",
              target: "useLocalStorageOrderAndVisibility",
            },
            {
              target: "renderedTableStorageSupport",
            },
          ],
        },
      },
    },
  },
  {
    actions: actions as any,
    services,
    guards: guards as any,
  },
);

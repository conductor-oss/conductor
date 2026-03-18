import { DoneInvokeEvent, assign } from "xstate";
import _keys from "lodash/keys";
import _isEmpty from "lodash/isEmpty";
import _isUndefined from "lodash/isUndefined";
import _path from "lodash/fp/path";
import {
  SelectWorkflowNameEvent,
  StartSubWfNameVersionMachineContext,
} from "./types";

export const persistWfName = assign<
  StartSubWfNameVersionMachineContext,
  SelectWorkflowNameEvent
>((_, { name }) => ({
  workflowName: name,
}));

export const persistFetchedNamesAndVersions = assign<
  StartSubWfNameVersionMachineContext,
  DoneInvokeEvent<Map<string, number[]>>
>((_, { data }: { data: Map<string, number[]> }) => {
  const obj = Object.fromEntries(data.entries());
  return {
    fetchedNamesAndVersions: obj,
  };
});

export const persistOptions = assign<StartSubWfNameVersionMachineContext>(
  (context) => {
    const namesAndVersinKeys = _keys(context?.fetchedNamesAndVersions);
    const wfNameOptions =
      namesAndVersinKeys.length === 0 ? [] : namesAndVersinKeys;

    const availableVersions =
      _isUndefined(context.workflowName) && !_isEmpty(wfNameOptions)
        ? []
        : _path(context.workflowName, context.fetchedNamesAndVersions);
    return {
      wfNameOptions: wfNameOptions,
      availableVersions: availableVersions,
    };
  },
);

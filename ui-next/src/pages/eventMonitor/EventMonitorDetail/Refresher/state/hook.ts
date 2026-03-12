import { useMachine, useSelector } from "@xstate/react";
import { refreshMachine } from "./machine";
import { State } from "xstate";
import {
  PageType,
  RefreshMachineContext,
  RefreshMachineEventTypes,
  RefreshMachineStates,
} from "./types";
import { useAuthHeaders } from "utils/query";
import { useCallback, useContext, useEffect } from "react";
import { MessageContext } from "components/v1/layout/MessageContext";

export const useRefreshMachine = (
  pageType?: PageType,
  eventName?: string,
  timeRange?: number,
) => {
  const authHeaders = useAuthHeaders();
  const { setMessage } = useContext(MessageContext);
  const [, send, service] = useMachine(refreshMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
      pageType,
    },
    actions: {
      showErrorMessage: (__, { data }: any) => {
        setMessage({
          text: data?.message ?? "Failed to fetch event monitor data",
          severity: "error",
        });
      },
    },
  });

  const refreshInterval = useSelector(
    service,
    (state: State<RefreshMachineContext>) => state.context.durationSet,
  );

  const eventMonitorData = useSelector(
    service,
    (state: State<RefreshMachineContext>) => state.context.eventData,
  );

  const eventListData = useSelector(
    service,
    (state: State<RefreshMachineContext>) => state.context.eventListData,
  );

  const isFetching = useSelector(
    service,
    (state: State<RefreshMachineContext>) =>
      state.matches(RefreshMachineStates.FETCH_DATA) ||
      state.matches(RefreshMachineStates.FETCH_EVENT_LIST_DATA),
  );
  const isError = useSelector(service, (state: State<RefreshMachineContext>) =>
    state.matches(RefreshMachineStates.ERROR),
  );

  const elapsed = useSelector(
    service,
    (state: State<RefreshMachineContext>) => state.context.elapsed,
  );

  const changeRefreshRate = (value: number) => {
    send({
      type: RefreshMachineEventTypes.UPDATE_DURATION,
      value,
    });
  };
  const handleRefresh = () =>
    send({
      type: RefreshMachineEventTypes.REFRESH,
    });

  const persistEventNameAndTime = useCallback(
    (eventName: string, timeRange: number) =>
      send({
        type: RefreshMachineEventTypes.PERSIST_EVENT_NAME_AND_TIME,
        data: {
          eventName,
          timeRange,
        },
      }),
    [send],
  );

  useEffect(() => {
    if (eventName && timeRange) {
      persistEventNameAndTime(eventName, timeRange);
    }
  }, [eventName, persistEventNameAndTime, timeRange]);

  return [
    {
      refreshInterval,
      elapsed,
      eventMonitorData,
      isFetching,
      eventListData,
      isError,
    },
    { changeRefreshRate, handleRefresh },
  ] as const;
};

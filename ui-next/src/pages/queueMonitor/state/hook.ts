import { useEffect, useMemo, useRef } from "react";
import { useAuthHeaders } from "utils/query";
import { useMachine } from "@xstate/react";
import { queueMonitorMachine } from "./machine";
import { QueueMachineEventTypes, QueueMonitorMachineEvents } from "./types";
import { ActorRef } from "xstate";
import { useLocation } from "react-router";
import qs from "qs";
import fastDeepEqual from "fast-deep-equal";
import { filterOptionOrNot } from "../helpers";

export const useQueueMachine = (): ActorRef<QueueMonitorMachineEvents> => {
  const authHeaders = useAuthHeaders();
  const { search } = useLocation();
  const [, send, service] = useMachine(queueMonitorMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
    },
  });

  const filterOptions = useMemo(() => {
    const queryParams = qs.parse(search, { ignoreQueryPrefix: true });
    return {
      queue: filterOptionOrNot("queue", queryParams),
      worker: filterOptionOrNot("worker", queryParams),
      lastPollTime: filterOptionOrNot("lastPollTime", queryParams),
    };
  }, [search]);

  // Quick search and pagination also live in the location's search string
  // (?search=, ?page=), and those are applied client-side against data the
  // machine already holds. Keying the fetch on the raw search string re-hit
  // both queue endpoints on every keystroke, so only forward a new object
  // once the filter values themselves differ.
  const lastFetchedFilterOptions = useRef(filterOptions);
  if (!fastDeepEqual(lastFetchedFilterOptions.current, filterOptions)) {
    lastFetchedFilterOptions.current = filterOptions;
  }
  const filterOptionsToFetch = lastFetchedFilterOptions.current;

  useEffect(() => {
    send({
      type: QueueMachineEventTypes.FETCH_TASKS_QUEUE,
      ...filterOptionsToFetch,
    });
  }, [send, filterOptionsToFetch]);

  return service;
};

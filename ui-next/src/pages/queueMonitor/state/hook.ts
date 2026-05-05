import { useEffect, useMemo } from "react";
import { useAuthHeaders } from "utils/query";
import { useMachine } from "@xstate/react";
import { queueMonitorMachine } from "./machine";
import { QueueMachineEventTypes, QueueMonitorMachineEvents } from "./types";
import { ActorRef } from "xstate";
import { useLocation } from "react-router";
import qs from "qs";
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

  const queryParams = useMemo(
    () => qs.parse(search, { ignoreQueryPrefix: true }),
    [search],
  );

  useEffect(() => {
    send({
      type: QueueMachineEventTypes.FETCH_TASKS_QUEUE,
      queue: filterOptionOrNot("queue", queryParams),
      worker: filterOptionOrNot("worker", queryParams),
      lastPollTime: filterOptionOrNot("lastPollTime", queryParams),
    });
  }, [send, queryParams]);

  return service;
};

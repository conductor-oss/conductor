import { useFetch } from "./common";
import { useMemo } from "react";
import { fetchWithContext, useFetchContext } from "../plugins/fetch";
import { useMutation } from "react-query";
import Path from "../utils/path";

export function useEventHandler(eventHandlerEvent, eventHandlerName, defaultEventHandler) {
  let path;
  if (eventHandlerEvent) {
    path = new Path(`/event/${eventHandlerEvent}`);
    path.search.append('activeOnly', false)
  }

  const query = useFetch(["eventHandlerDef", eventHandlerEvent], path);

  const eventHandler = useMemo(
    () => {
      const data = query.data;

      if (!data || data.leading < 1) {
        return defaultEventHandler;
      }

      return data ? data.find((row) => row.name === eventHandlerName) : defaultEventHandler
    },
    [query.data, eventHandlerName, defaultEventHandler]
  );

  return {
    ...query,
    eventHandler
  }
}

export function useEventHandlerDefs() {
  return useFetch(["eventHandlerDefs"], "/event");
}

export function useEventHandlerNames() {
  const { data } = useEventHandlerDefs();
  return useMemo(
    () => (data ? Array.from(new Set(data.map((def) => def.name))).sort() : []),
    [data]
  );
}

export function useSaveEventHandler(callbacks) {
  const path = "/event";
  const fetchContext = useFetchContext();

  return useMutation(({ body, isNew }) => {
    console.log(isNew);
    return fetchWithContext(path, fetchContext, {
      method: isNew ? "post" : "put",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });
  }, callbacks);
}
import { useEnv } from "./env";

export function useFetchContext() {
  const { stack } = useEnv();
  return {
    stack,
    ready: true,
  };
}
export function fetchWithContext(path, context, fetchParams) {
  const newParams = { ...fetchParams };

  const newPath = `/api/${path}`;
  const cleanPath = newPath.replace(/([^:]\/)\/+/g, "$1"); // Cleanup duplicated slashes

  return fetch(cleanPath, newParams).then(res => res.text())
    .then(text => {
      if(!text || text.length === 0){
        return null;
      }
      else {
        return JSON.parse(text);
      }
    })
}

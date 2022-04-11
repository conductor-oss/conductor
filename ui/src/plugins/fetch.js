import { useEnv } from "./env";

export function useFetchContext() {
  const { stack } = useEnv();
  return {
    stack,
    ready: true,
  };
}
export function fetchWithContext(
  path,
  context,
  fetchParams,
  isJsonResponse = true
) {
  const newParams = { ...fetchParams };

  const newPath = `/api/${path}`;
  const cleanPath = newPath.replace(/([^:]\/)\/+/g, "$1"); // Cleanup duplicated slashes

  return fetch(cleanPath, newParams)
    .then((res) => Promise.all([res, res.text()]))
    .then(([res, text]) => {
      if (!res.ok) {
        // get error message from body or default to response status
        const error = text || res.status;
        return Promise.reject(error);
      } else if (!text || text.length === 0) {
        return null;
      } else if (!isJsonResponse) {
        return text;
      } else {
        try {
          return JSON.parse(text);
        } catch (e) {
          return text;
        }
      }
    });
}

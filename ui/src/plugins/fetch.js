import { getBasename } from "../utils/helpers";
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

  const basename = getBasename();
  const newPath = basename + `api/${path}`;
  const cleanPath = cleanDuplicateSlash(newPath); // Cleanup duplicated slashes

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

/**
 * @param {string} path 
 * @returns path with '/' not duplicated, except at ://
 * 
 */
export function cleanDuplicateSlash(path) {
  return path.replace(/(:\/\/)\/*|(\/)+/g, "$1$2");
}

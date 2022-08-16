import handleError from "../../utils/handleError";
import AppContext from "./AppContext";

export default function DefaultAppContextProvider({ ...props }) {
  return (
    <AppContext.Provider
      value={{
        ready: true,
        stack: "default",
        defaultStack: "default",
        customTypeRenderers: {},
        fetchWithContext: function (path, fetchParams) {
          const newParams = { ...fetchParams };

          const newPath = `/api/${path}`;
          const cleanPath = newPath.replace(/([^:]\/)\/+/g, "$1"); // Cleanup duplicated slashes

          return fetch(cleanPath, newParams).then(handleError);
        },
      }}
      {...props}
    />
  );
}

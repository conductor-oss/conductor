/**
 * Fetch utilities for OSS mode.
 *
 * This simplified version removes auth token handling since
 * OSS mode does not use authentication.
 */
import { MessageContext } from "components/providers/messageContext";
import { useContext } from "react";
import { IObject } from "types/common";
import { getErrorMessage, tryToJson } from "utils/utils";
import { useEnv as hardcodeEnv } from "./env";

const { VITE_ENVIRONMENT, VITE_WF_SERVER } = process.env;

export function fetchContextNonHook() {
  const { stack } = hardcodeEnv();

  return {
    stack,
    ready: true,
  };
}

export function useFetchContext() {
  const contextNonHook = fetchContextNonHook();
  const { setMessage } = useContext(MessageContext);

  return {
    ...contextNonHook,
    setMessage,
  };
}

export async function fetchWithContext(
  path: string,
  context: IObject,
  fetchParams: IObject,
  isText?: boolean,
  throwOnError = true,
): Promise<any> {
  const newParams = { ...fetchParams };

  // Need for build version (can't use proxy)
  const newPath = `${
    VITE_ENVIRONMENT === "test" ? VITE_WF_SERVER : ""
  }/api/${path}`;

  const cleanPath = newPath.replace(/([^:]\/)\/+/g, "$1"); // Cleanup duplicated slashes

  const res = await fetch(cleanPath, newParams);

  // Handle error cases
  if (!res.ok) {
    const hasContext = context && context?.setMessage != null;
    // 1. Using global message
    if (hasContext && !throwOnError) {
      const errorMessage = await getErrorMessage(res);
      context.setMessage({ text: errorMessage, severity: "error" });

      return null;
    }

    // 2. Throw the error to handle locally
    throw res;
  }

  const text = await res.text();

  if (!text || text.length === 0) {
    return null;
  }

  return isText ? text : tryToJson(text);
}

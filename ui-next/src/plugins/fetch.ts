/**
 * Fetch utilities for OSS mode.
 *
 * This simplified version removes auth token handling since
 * OSS mode does not use authentication.
 */
import { MessageContext } from "components/providers/messageContext";
import { useContext } from "react";
import { IObject } from "types/common";
import { asciiSafeJson } from "utils/strings";
import { getErrorMessage, tryToJson } from "utils/utils";
import { useEnv as hardcodeEnv } from "./env";
import { pluginRegistry } from "./registry";

const { VITE_ENVIRONMENT, VITE_WF_SERVER } = process.env;

export const ORG_ID_HEADER_NAME = "X-Org-Id";

/**
 * Adds X-Org-Id from the plugin-registered org ID, unless the caller set one.
 * Returns the headers unchanged when no org ID is registered (OSS).
 */
export function withOrgIdHeader(
  headers?: ConstructorParameters<typeof Headers>[0],
): ConstructorParameters<typeof Headers>[0] {
  const orgId = pluginRegistry.getOrgId();
  if (!orgId) {
    return headers;
  }
  const merged = new Headers(headers);
  if (!merged.has(ORG_ID_HEADER_NAME)) {
    merged.set(ORG_ID_HEADER_NAME, orgId);
  }
  return merged;
}

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

  const headers = withOrgIdHeader(newParams.headers);
  if (headers !== newParams.headers) {
    newParams.headers = headers;
  }

  if (typeof newParams.body === "string") {
    try {
      JSON.parse(newParams.body);
      newParams.body = asciiSafeJson(newParams.body);
    } catch {
      // body is not valid JSON (e.g. raw prompt template); leave it unchanged
    }
  }

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

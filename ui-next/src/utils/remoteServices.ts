/**
 * Utility functions for remote service operations.
 * Extracted from pages/remoteServices so OSS code can use them without
 * importing from an enterprise page.
 */

export function splitHostAndPort(url = "") {
  // Split by ":" to separate host and port
  const [host, port] = url.split(/:(?=\d+$)/);

  // If there's no port, return null for port
  return { host, port: Number(port) || null };
}

export function replaceDynamicParams(
  url: string,
  params: Record<string, Record<string, unknown>>,
): { url: string; headers?: Record<string, string> } {
  // Replace path parameters in the URL
  const pathReplaced = url.replace(/\{(\w+)\}/g, (_, key: string): string => {
    const param = params[key];
    return param && param?.type === "path" && param?.value != null
      ? (param.value as string)
      : `{${key}}`; // fallback to original if missing
  });

  // Collect query parameters
  const queryParams = Object.values(params)
    ?.filter(
      (param) =>
        param.type === "query" &&
        param.value != null &&
        param.value !== undefined &&
        param.value !== "",
    )
    ?.map((param) => `${param?.name}=${param.value}`);

  const queryString = queryParams.length ? `?${queryParams.join("&")}` : "";

  // Collect headers if available
  const headersEntries = Object.values(params)
    ?.filter(
      (param) =>
        param.type === "header" &&
        param.value != null &&
        param.value !== undefined &&
        param.value !== "",
    )
    ?.map((param) => [param.name as string, String(param.value)]);
  const headers =
    headersEntries.length > 0 ? Object.fromEntries(headersEntries) : undefined;

  return {
    url: pathReplaced + queryString,
    ...(headers ? { headers } : {}),
  };
}

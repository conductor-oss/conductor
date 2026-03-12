export function getHttpStatusText(code: string): string {
  const statusCodes: Record<string, string> = {
    "400": "Bad Request",
    "401": "Unauthorized",
    "403": "Forbidden",
    "404": "Not Found",
    "500": "Internal Server Error",
    "502": "Bad Gateway",
    "503": "Service Unavailable",
    "504": "Gateway Timeout",
  };
  return statusCodes[code] || "Unknown Error";
}

import { and, isStringControl, rankWith, schemaMatches } from "@jsonforms/core";

export const MAX_ZIP_BYTES = 10 * 1024 * 1024;

// Browsers take hundreds of milliseconds to lay out an input holding
// megabytes of text, so larger values are summarized instead of shown.
export const SUMMARY_MIN_LENGTH = 4096;

// Base64 of the ZIP signatures "PK\x03\x04" (local file header) and
// "PK\x05\x06" (end of central directory, i.e. an empty archive).
const ZIP_BASE64_PREFIXES = ["UEsDB", "UEsFBg"];

export function isZipBase64(value: string) {
  return ZIP_BASE64_PREFIXES.some((prefix) => value.startsWith(prefix));
}

export function decodedSize(base64: string) {
  const padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
  const bytes = Math.floor((base64.length * 3) / 4) - padding;
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${bytes} B`;
}

// Picks the ZIP control for string fields declared as Base64-encoded ZIPs.
export const zipFileTester = rankWith(
  5,
  and(
    isStringControl,
    schemaMatches(
      (schema) =>
        "contentEncoding" in schema &&
        "contentMediaType" in schema &&
        schema.contentEncoding === "base64" &&
        schema.contentMediaType === "application/zip",
    ),
  ),
);

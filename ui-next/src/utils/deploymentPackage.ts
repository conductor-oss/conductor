export const MAX_INLINE_ZIP_BYTES = 1024 * 1024;
export const packageFields = [
  "zipFileBase64",
  "s3Bucket",
  "s3Key",
  "s3ObjectVersion",
];

export const hasDeploymentPackage = (schema: Record<string, any>) =>
  schema.properties?.zipFileBase64?.contentMediaType === "application/zip" &&
  !!schema.properties.s3Bucket;

export const withoutDeploymentPackage = (input: Record<string, any>) =>
  Object.fromEntries(
    Object.entries(input).filter(([key]) => !packageFields.includes(key)),
  );

// Files stay local; only the raw Base64 content is saved in the task input.
// Validate before reading to keep oversized artifacts out of browser memory.
export async function readDeploymentZip(file: File): Promise<string> {
  if (!file.name.toLowerCase().endsWith(".zip"))
    throw new Error("Choose a .zip deployment package.");
  if (file.size === 0 || file.size > MAX_INLINE_ZIP_BYTES)
    throw new Error(
      "Inline ZIP must be non-empty and at most 1 MiB. Use S3 for larger packages.",
    );
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () =>
      reject(new Error("Could not read the ZIP. Select it again."));
    reader.onload = () => {
      const base64 = (reader.result as string).split(",")[1];
      const signature = atob(base64.slice(0, 8));
      if (
        !signature.startsWith("PK\x03\x04") &&
        !signature.startsWith("PK\x05\x06")
      ) {
        reject(new Error("The selected file is not a ZIP archive."));
        return;
      }
      resolve(base64);
    };
    reader.readAsDataURL(file);
  });
}

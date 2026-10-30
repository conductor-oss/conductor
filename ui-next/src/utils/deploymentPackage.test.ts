import { afterEach, expect, it, vi } from "vitest";
import {
  hasDeploymentPackage,
  MAX_INLINE_ZIP_BYTES,
  readDeploymentZip,
  withoutDeploymentPackage,
} from "./deploymentPackage";
import schema from "pages/definition/EditorPanel/TaskFormTab/forms/__fixtures__/lambdaCreateSchema.json";

afterEach(() => vi.restoreAllMocks());

it("recognizes only package schemas and preserves unrelated input", () => {
  expect(hasDeploymentPackage({})).toBeFalsy();
  expect(hasDeploymentPackage({ properties: {} })).toBeFalsy();
  expect(
    hasDeploymentPackage({ properties: { zipFileBase64: {} } }),
  ).toBeFalsy();
  expect(
    hasDeploymentPackage({
      properties: { zipFileBase64: { contentMediaType: "application/zip" } },
    }),
  ).toBeFalsy();
  expect(hasDeploymentPackage(schema)).toBe(true);
  expect(
    withoutDeploymentPackage({
      functionName: "f",
      integrationName: "saved",
      zipFileBase64: "data",
      s3Bucket: "b",
      s3Key: "k",
      s3ObjectVersion: "v",
    }),
  ).toEqual({ functionName: "f", integrationName: "saved" });
});

it("reads ZIP bytes as raw base64, including the upload size boundary and empty-archive signature", async () => {
  const bytes = new Uint8Array(MAX_INLINE_ZIP_BYTES);
  bytes.set([0x50, 0x4b, 3, 4]);
  const base64 = await readDeploymentZip(new File([bytes], "CODE.ZIP"));
  expect(atob(base64).length).toBe(MAX_INLINE_ZIP_BYTES);
  expect(base64.startsWith("data:")).toBe(false);
  expect(
    await readDeploymentZip(
      new File([new Uint8Array([0x50, 0x4b, 5, 6])], "empty.zip"),
    ),
  ).toBe("UEsFBg==");
});

it("rejects wrong extension, empty, oversized and non-ZIP files without encoding invalid input", async () => {
  await expect(
    readDeploymentZip(new File(["code"], "code.txt")),
  ).rejects.toThrow(".zip");
  await expect(readDeploymentZip(new File([], "code.zip"))).rejects.toThrow(
    "non-empty",
  );
  await expect(
    readDeploymentZip(
      new File([new Uint8Array(MAX_INLINE_ZIP_BYTES + 1)], "code.zip"),
    ),
  ).rejects.toThrow("1 MiB");
  await expect(
    readDeploymentZip(new File(["not a zip"], "code.zip")),
  ).rejects.toThrow("not a ZIP");
});

it("reports read failures without exposing file content", async () => {
  vi.spyOn(FileReader.prototype, "readAsDataURL").mockImplementation(function (
    this: FileReader,
  ) {
    this.dispatchEvent(new ProgressEvent("error"));
  });
  await expect(
    readDeploymentZip(new File(["secret"], "code.zip")),
  ).rejects.toThrow("Could not read");
});

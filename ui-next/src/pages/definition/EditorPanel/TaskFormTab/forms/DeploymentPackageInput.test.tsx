import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { useState } from "react";
import { afterEach, expect, it, vi } from "vitest";
import DeploymentPackageInput from "./DeploymentPackageInput";
import * as deployment from "utils/deploymentPackage";

afterEach(() => vi.restoreAllMocks());
const zip = new File([new Uint8Array([0x50, 0x4b, 3, 4])], "function.zip");
function Form({
  initial = {},
  changed = vi.fn(),
}: {
  initial?: Record<string, any>;
  changed?: (input: any) => void;
}) {
  const [input, update] = useState({ functionName: "f", ...initial });
  return (
    <DeploymentPackageInput
      input={input}
      onChange={(next) => {
        update(next as typeof input);
        changed(next);
      }}
    />
  );
}
function source(name: string) {
  fireEvent.mouseDown(screen.getByRole("combobox", { name: "Package source" }));
  fireEvent.click(screen.getByRole("option", { name }));
}
const upload = (files: File[] | null) =>
  fireEvent.change(screen.getByLabelText("Upload deployment ZIP"), {
    target: { files },
  });

it("uploads, replaces and removes a ZIP without altering task inputs", async () => {
  const changed = vi.fn();
  render(<Form changed={changed} />);
  expect(screen.getByText("No ZIP selected.")).toBeInTheDocument();
  upload(null);
  upload([]);
  expect(changed).not.toHaveBeenCalled();
  upload([zip]);
  expect(screen.getByText("Reading ZIP…")).toBeInTheDocument();
  await waitFor(() =>
    expect(changed).toHaveBeenCalledWith({
      functionName: "f",
      zipFileBase64: "UEsDBA==",
    }),
  );
  expect(screen.getByText("function.zip")).toBeInTheDocument();
  upload([zip]);
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByRole("button", { name: "Remove ZIP" }));
  expect(changed).toHaveBeenLastCalledWith({ functionName: "f" });
});

it("keeps Base64/expression input compatible and clears only fields of the inactive source", () => {
  const changed = vi.fn();
  render(
    <Form
      initial={{ zipFileBase64: "${workflow.input.zip}" }}
      changed={changed}
    />,
  );
  expect(
    screen.getByText(/inline package or expression is attached/),
  ).toBeInTheDocument();
  source("Base64 / expression (advanced)");
  expect(screen.getByLabelText(/ZIP Base64 or expression/)).toHaveValue(
    "${workflow.input.zip}",
  );
  fireEvent.change(screen.getByLabelText(/ZIP Base64 or expression/), {
    target: { value: "UEsDBA==" },
  });
  source("Upload ZIP");
  expect(changed).toHaveBeenLastCalledWith({
    functionName: "f",
    zipFileBase64: "UEsDBA==",
  });
  source("S3 object");
  expect(changed).toHaveBeenLastCalledWith({
    functionName: "f",
    s3Bucket: "",
    s3Key: "",
  });
  fireEvent.change(screen.getByLabelText(/S3 bucket/), {
    target: { value: "bucket" },
  });
  fireEvent.change(screen.getByLabelText(/S3 object key/), {
    target: { value: "code.zip" },
  });
  fireEvent.change(screen.getByLabelText(/S3 object version/), {
    target: { value: "v7" },
  });
  expect(changed).toHaveBeenLastCalledWith({
    functionName: "f",
    s3Bucket: "bucket",
    s3Key: "code.zip",
    s3ObjectVersion: "v7",
  });
  fireEvent.change(screen.getByLabelText(/S3 object version/), {
    target: { value: "" },
  });
  expect(changed).toHaveBeenLastCalledWith({
    functionName: "f",
    s3Bucket: "bucket",
    s3Key: "code.zip",
  });
  source("Base64 / expression (advanced)");
  expect(changed).toHaveBeenLastCalledWith({ functionName: "f" });
});

it.each([
  { s3Bucket: "bucket" },
  { s3Key: "code.zip" },
  { s3ObjectVersion: "v7" },
])("recognizes existing partial S3 input %s", (initial) => {
  render(<Form initial={initial} />);
  expect(
    screen.getByRole("combobox", { name: "Package source" }),
  ).toHaveTextContent("S3 object");
});

it("preserves an existing package when a new upload is invalid", async () => {
  const changed = vi.fn();
  render(<Form initial={{ zipFileBase64: "existing" }} changed={changed} />);
  upload([new File(["bad"], "bad.zip")]);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "not a ZIP archive",
  );
  expect(changed).not.toHaveBeenCalled();
});

it.each([true, false])(
  "ignores a stale upload after switching source (success=%s)",
  async (success) => {
    let resolve!: (value: string) => void;
    let reject!: (error: Error) => void;
    vi.spyOn(deployment, "readDeploymentZip").mockReturnValue(
      new Promise((res, rej) => {
        resolve = res;
        reject = rej;
      }),
    );
    const changed = vi.fn();
    render(<Form changed={changed} />);
    upload([zip]);
    source("S3 object");
    await act(async () => {
      if (success) resolve("UEsDBA==");
      else reject(new Error("stale"));
    });
    expect(changed).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  },
);

it("merges a completed upload with the latest unrelated edits and ignores completion after unmount", async () => {
  let resolve!: (value: string) => void;
  vi.spyOn(deployment, "readDeploymentZip").mockImplementation(
    () =>
      new Promise((res) => {
        resolve = res;
      }),
  );
  const changed = vi.fn();
  const view = render(
    <DeploymentPackageInput
      input={{ functionName: "old" }}
      onChange={changed}
    />,
  );
  upload([zip]);
  view.rerender(
    <DeploymentPackageInput
      input={{ functionName: "edited" }}
      onChange={changed}
    />,
  );
  await act(async () => resolve("UEsDBA=="));
  expect(changed).toHaveBeenLastCalledWith({
    functionName: "edited",
    zipFileBase64: "UEsDBA==",
  });
  upload([zip]);
  view.unmount();
  await act(async () => resolve("UEsDBA=="));
  expect(changed).toHaveBeenCalledTimes(1);
});

import { useState } from "react";
import { MemoryRouter } from "react-router";
import { Provider as ThemeProvider } from "theme/material/provider";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { IntegrationTaskForm } from "./IntegrationTaskForm";
import { TaskFormProps } from "./types";
import { SUMMARY_MIN_LENGTH } from "./zipFile";
import { useFetch } from "utils/query";

const FORM_SCHEMA = {
  type: "object",
  properties: {
    source: {
      type: "object",
      default: { sourceType: "BASE64" },
      properties: {
        sourceType: { type: "string", enum: ["BASE64", "CLOUD_LOCATION"] },
        zipFileBase64: {
          type: "string",
          title: "ZIP file (Base64)",
          contentEncoding: "base64",
          contentMediaType: "application/zip",
        },
        sourceLocation: { type: "string" },
        objectVersion: { type: "string" },
      },
    },
  },
  "x-ui-schema": {
    type: "VerticalLayout",
    elements: [
      { type: "Control", scope: "#/properties/source/properties/sourceType" },
      {
        type: "Control",
        scope: "#/properties/source/properties/zipFileBase64",
        rule: {
          effect: "SHOW",
          condition: {
            scope: "#/properties/source/properties/sourceType",
            schema: { const: "BASE64" },
            failWhenUndefined: true,
          },
        },
      },
      {
        type: "VerticalLayout",
        rule: {
          effect: "SHOW",
          condition: {
            scope: "#/properties/source/properties/sourceType",
            schema: { const: "CLOUD_LOCATION" },
            failWhenUndefined: true,
          },
        },
        elements: [
          {
            type: "Control",
            scope: "#/properties/source/properties/sourceLocation",
          },
          {
            type: "Control",
            scope: "#/properties/source/properties/objectVersion",
          },
        ],
      },
    ],
  },
};

vi.mock("utils/query", () => ({ useFetch: vi.fn() }));
vi.mock("utils/hooks/useIntegrationProviders", () => ({
  useIntegrationProviders: () => ({
    integrations: [{ name: "test-integration", status: "active" }],
  }),
  useIntegrationApis: () => ({ tools: [{ api: "Create Function" }] }),
}));
vi.mock("./MaybeVariable", () => ({
  MaybeVariable: ({ children }: { children: React.ReactNode }) => children,
}));

function Harness({ initial = {} }: { initial?: Record<string, unknown> }) {
  const [task, setTask] = useState<TaskFormProps["task"]>({
    inputParameters: {
      integrationName: "test-integration",
      method: "Create Function",
      unrelated: "preserved",
      ...initial,
    },
  });
  return (
    <ThemeProvider>
      <MemoryRouter>
        <IntegrationTaskForm task={task} onChange={setTask} />
        <output data-testid="input">
          {JSON.stringify(task.inputParameters)}
        </output>
      </MemoryRouter>
    </ThemeProvider>
  );
}
const input = () => JSON.parse(screen.getByTestId("input").textContent!);
const selectSource = (value: string) => {
  fireEvent.mouseDown(screen.getByRole("combobox", { name: /source type/i }));
  fireEvent.click(screen.getByRole("option", { name: value }));
};

describe("IntegrationTaskForm", () => {
  const serve = () =>
    vi.mocked(useFetch).mockReturnValue({
      data: { inputSchema: { data: FORM_SCHEMA } },
      isLoading: false,
    } as ReturnType<typeof useFetch>);
  it("uses standard enum and visibility rules, clears inactive data and preserves unrelated inputs", async () => {
    serve();
    render(<Harness />);
    await waitFor(() => expect(input().source.sourceType).toBe("BASE64"));
    expect(
      screen.getAllByRole("combobox", { name: /source type/i }),
    ).toHaveLength(1);
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: /zip.*base.*64/i }), {
      target: { value: "UEsDBA==" },
    });
    await waitFor(() => expect(input().source.zipFileBase64).toBe("UEsDBA=="));
    expect(
      screen.queryByRole("textbox", { name: /source location/i }),
    ).not.toBeInTheDocument();
    selectSource("CLOUD_LOCATION");
    const location = await screen.findByRole("textbox", {
      name: /source location/i,
    });
    expect(
      screen.queryByRole("textbox", { name: /zip.*base.*64/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await waitFor(() =>
      expect(input().source).toEqual({ sourceType: "CLOUD_LOCATION" }),
    );
    fireEvent.change(location, { target: { value: "gs://test/code.zip" } });
    fireEvent.change(screen.getByRole("textbox", { name: /object version/i }), {
      target: { value: "42" },
    });
    await waitFor(() => expect(input().source.objectVersion).toBe("42"));
    selectSource("BASE64");
    await screen.findByRole("textbox", { name: /zip.*base.*64/i });
    await waitFor(() =>
      expect(input().source).toEqual({ sourceType: "BASE64" }),
    );
    expect(input().unrelated).toBe("preserved");
  });
  it("preserves saved cloud inputs on load", async () => {
    serve();
    const source = {
      sourceType: "CLOUD_LOCATION",
      sourceLocation: "gs://test/code.zip",
      objectVersion: "42",
    };
    render(<Harness initial={{ source }} />);
    expect(
      await screen.findByRole("textbox", { name: /source location/i }),
    ).toHaveValue(source.sourceLocation);
    expect(input().source).toEqual(source);
    expect(
      screen.queryByRole("textbox", { name: /zip.*base.*64/i }),
    ).not.toBeInTheDocument();
  });

  it("stores chosen ZIP bytes as Base64 using the existing source inputs", async () => {
    serve();
    render(<Harness />);
    const picker = await screen.findByLabelText(/choose zip for/i);
    const helper = /choose a zip up to 10 mib/i;
    expect(screen.getByText(helper)).toBeInTheDocument();
    const file = new File(
      [new Uint8Array([80, 75, 3, 4, 116, 101, 115, 116])],
      "function.zip",
      { type: "application/zip" },
    );
    fireEvent.change(picker, { target: { files: [file] } });
    await waitFor(() =>
      expect(input().source).toEqual({
        sourceType: "BASE64",
        zipFileBase64: "UEsDBHRlc3Q=",
      }),
    );
    expect(input().unrelated).toBe("preserved");
    expect(input().source).not.toHaveProperty("fileName");
    // Once loaded, the field needs no guidance text.
    await waitFor(() =>
      expect(screen.queryByText(helper)).not.toBeInTheDocument(),
    );
    // Even a tiny ZIP is summarized rather than shown as Base64.
    expect(
      await screen.findByDisplayValue("ZIP file · 8 B"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("textbox", { name: /zip.*base.*64/i }),
    ).toHaveAttribute("readonly");
    // Selecting the same file again remains possible.
    fireEvent.change(screen.getByLabelText(/choose zip for/i), {
      target: { files: [file] },
    });
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Replace" })).toBeEnabled(),
    );
    selectSource("CLOUD_LOCATION");
    await screen.findByRole("textbox", { name: /source location/i });
    expect(screen.queryByLabelText(/choose zip for/i)).not.toBeInTheDocument();
    await waitFor(() =>
      expect(input().source).toEqual({ sourceType: "CLOUD_LOCATION" }),
    );
  });

  it.each(["extension", "empty", "oversized"])(
    "rejects an %s file without overwriting saved inputs",
    async (invalid) => {
      serve();
      render(
        <Harness
          initial={{
            source: { sourceType: "BASE64", zipFileBase64: "saved-value" },
          }}
        />,
      );
      const file = new File(
        invalid === "empty" ? [] : ["test"],
        invalid === "extension" ? "function.txt" : "function.zip",
      );
      if (invalid === "oversized")
        Object.defineProperty(file, "size", { value: 10 * 1024 * 1024 + 1 });
      fireEvent.change(await screen.findByLabelText(/choose zip for/i), {
        target: { files: [file] },
      });
      expect(await screen.findByRole("alert")).toHaveTextContent(
        invalid === "extension"
          ? "Choose a .zip"
          : "non-empty ZIP up to 10 MiB",
      );
      expect(input().source.zipFileBase64).toBe("saved-value");
    },
  );

  it("does not restore ZIP data after the user switches to a cloud source during a read", async () => {
    // Hold the read open; the reader is recovered from the mock's `this`.
    const read = vi
      .spyOn(FileReader.prototype, "readAsDataURL")
      .mockImplementation(() => {});
    try {
      serve();
      render(<Harness />);
      fireEvent.change(await screen.findByLabelText(/choose zip for/i), {
        target: { files: [new File(["test"], "function.zip")] },
      });
      selectSource("CLOUD_LOCATION");
      await screen.findByRole("textbox", { name: /source location/i });
      const pending = read.mock.contexts[0] as FileReader;
      Object.defineProperty(pending, "result", {
        value: "data:application/zip;base64,UEsDBA==",
      });
      pending.dispatchEvent(new ProgressEvent("load"));
      await waitFor(() =>
        expect(input().source).toEqual({ sourceType: "CLOUD_LOCATION" }),
      );
    } finally {
      read.mockRestore();
    }
  });

  it("summarizes a large value instead of rendering its Base64, and removing restores the text field", async () => {
    serve();
    const large = "QUFB".repeat(2000); // 8000 chars, 6000 bytes
    render(
      <Harness
        initial={{ source: { sourceType: "BASE64", zipFileBase64: large } }}
      />,
    );
    expect(
      await screen.findByDisplayValue("ZIP file · 5.9 KiB"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("textbox", { name: /zip.*base.*64/i }),
    ).toHaveAttribute("readonly");
    expect(screen.queryByDisplayValue(large)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/choose zip for/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove" }));
    await waitFor(() =>
      expect(input().source).toEqual({ sourceType: "BASE64" }),
    );
    expect(
      await screen.findByRole("textbox", { name: /zip.*base.*64/i }),
    ).toHaveValue("");
    expect(screen.getByRole("button", { name: "Choose ZIP" })).toBeEnabled();
    expect(
      screen.getByText("ZIP removed.", { exact: false }),
    ).toBeInTheDocument();
  });

  it("restores a removed ZIP with Undo", async () => {
    serve();
    const large = "QUFB".repeat(2000);
    render(
      <Harness
        initial={{ source: { sourceType: "BASE64", zipFileBase64: large } }}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Remove" }));
    await waitFor(() =>
      expect(input().source).toEqual({ sourceType: "BASE64" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Undo" }));
    await waitFor(() => expect(input().source.zipFileBase64).toBe(large));
    expect(
      await screen.findByDisplayValue("ZIP file · 5.9 KiB"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Undo" }),
    ).not.toBeInTheDocument();
  });

  it("drops the Undo option once a new value is entered", async () => {
    serve();
    render(
      <Harness
        initial={{
          source: { sourceType: "BASE64", zipFileBase64: "QUFB".repeat(2000) },
        }}
      />,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Remove" }));
    const field = await screen.findByRole("textbox", {
      name: /zip.*base.*64/i,
    });
    fireEvent.change(field, { target: { value: "${workflow.input.zip}" } });
    await waitFor(() =>
      expect(input().source.zipFileBase64).toBe("${workflow.input.zip}"),
    );
    expect(
      screen.queryByRole("button", { name: "Undo" }),
    ).not.toBeInTheDocument();
    // Expressions aren't ZIP data, so they stay editable as text.
    expect(field).toHaveValue("${workflow.input.zip}");
  });

  it("switches to the summary after choosing a ZIP above the display threshold", async () => {
    serve();
    render(<Harness />);
    const bytes = new Uint8Array(SUMMARY_MIN_LENGTH);
    bytes.set([80, 75, 3, 4]);
    fireEvent.change(await screen.findByLabelText(/choose zip for/i), {
      target: { files: [new File([bytes], "function.zip")] },
    });
    expect(
      await screen.findByDisplayValue("ZIP file · 4.0 KiB"),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(input().source?.zipFileBase64).toMatch(/^UEsDBA/),
    );
    expect(
      screen.getByRole("textbox", { name: /zip.*base.*64/i }),
    ).toHaveAttribute("readonly");
    expect(screen.getByRole("button", { name: "Replace" })).toBeEnabled();
  });

  it("shows schema validation errors while summarized", async () => {
    const schema = structuredClone(FORM_SCHEMA);
    Object.assign(schema.properties.source.properties.zipFileBase64, {
      maxLength: 5000,
    });
    vi.mocked(useFetch).mockReturnValue({
      data: { inputSchema: { data: schema } },
      isLoading: false,
    } as ReturnType<typeof useFetch>);
    render(
      <Harness
        initial={{
          source: { sourceType: "BASE64", zipFileBase64: "QUFB".repeat(2000) },
        }}
      />,
    );
    expect(
      await screen.findByText(/must not have more than 5000 characters/i),
    ).toBeInTheDocument();
  });

  it("surfaces read errors and retains the previous Base64 input", async () => {
    const read = vi
      .spyOn(FileReader.prototype, "readAsDataURL")
      .mockImplementation(function (this: FileReader) {
        this.dispatchEvent(new ProgressEvent("error"));
      });
    try {
      serve();
      render(
        <Harness
          initial={{
            source: { sourceType: "BASE64", zipFileBase64: "saved-value" },
          }}
        />,
      );
      fireEvent.change(await screen.findByLabelText(/choose zip for/i), {
        target: { files: [new File(["test"], "function.zip")] },
      });
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Unable to read the ZIP",
      );
      expect(input().source.zipFileBase64).toBe("saved-value");
    } finally {
      read.mockRestore();
    }
  });
});

const DEFAULT_SCHEMA = {
  type: "object",
  properties: {
    useFunctionKey: { type: "boolean", default: true },
    retries: { type: "integer", default: 2 },
  },
};
const serveSchema = (schema: object, isLoading = false) =>
  vi.mocked(useFetch).mockReturnValue({
    data: isLoading ? undefined : { inputSchema: { data: schema } },
    isLoading,
  } as ReturnType<typeof useFetch>);

it.each([
  { type: "string" },
  { type: "string", contentEncoding: "base64", contentMediaType: "image/png" },
])("keeps ordinary and non-ZIP string controls unchanged", async (schema) => {
  serveSchema({ type: "object", properties: { payload: schema } });
  render(<Harness initial={{ payload: "${workflow.input.payload}" }} />);
  const text = await screen.findByRole("textbox", { name: /payload/i });
  expect(text).toHaveValue("${workflow.input.payload}");
  expect(screen.queryByLabelText(/choose zip for/i)).not.toBeInTheDocument();
  fireEvent.change(text, { target: { value: "updated" } });
  await waitFor(() => expect(input().payload).toBe("updated"));
});

it("uses JSON Forms to persist defaults with an automatically generated layout", async () => {
  serveSchema(DEFAULT_SCHEMA);
  render(<Harness />);
  await waitFor(() => expect(input().useFunctionKey).toBe(true));
  expect(input().retries).toBe(2);
  expect(
    screen.getByRole("checkbox", { name: /use function key/i }),
  ).toBeChecked();
  fireEvent.click(screen.getByRole("checkbox", { name: /use function key/i }));
  await waitFor(() => expect(input().useFunctionKey).toBe(false));
});

it.each([false, null, "${workflow.input.useKey}"])(
  "does not overwrite explicit input %s when applying defaults",
  async (value) => {
    serveSchema(DEFAULT_SCHEMA);
    render(<Harness initial={{ useFunctionKey: value }} />);
    await screen.findByRole("checkbox", { name: /use function key/i });
    await waitFor(() => expect(input().retries).toBe(2));
    expect(input().useFunctionKey).toBe(value);
  },
);

it("submits only visible fields, including when inactive values were loaded", async () => {
  serveSchema(FORM_SCHEMA);
  const source = {
    sourceType: "CLOUD_LOCATION",
    sourceLocation: "gs://saved/code.zip",
    objectVersion: "42",
    zipFileBase64: "UEsDBA==",
  };
  render(<Harness initial={{ source }} />);
  await waitFor(() =>
    expect(input().source).toEqual({
      sourceType: "CLOUD_LOCATION",
      sourceLocation: source.sourceLocation,
      objectVersion: "42",
    }),
  );
  expect(source.zipFileBase64).toBe("UEsDBA==");
  expect(
    screen.queryByRole("textbox", { name: /zip.*base.*64/i }),
  ).not.toBeInTheDocument();
});

it("renders ordinary inputs without UI metadata or conditional filtering", async () => {
  const { "x-ui-schema": _, ...schema } = FORM_SCHEMA;
  serveSchema(schema);
  const source = {
    sourceType: "BASE64",
    zipFileBase64: "UEsDBA==",
    sourceLocation: "gs://saved/code.zip",
    objectVersion: "42",
  };
  render(<Harness initial={{ source }} />);
  await screen.findByRole("textbox", { name: /source location/i });
  expect(screen.getByDisplayValue(/^ZIP file · /)).toBeInTheDocument();
  selectSource("CLOUD_LOCATION");
  await waitFor(() => expect(input().source.sourceType).toBe("CLOUD_LOCATION"));
  expect(input().source.zipFileBase64).toBe(source.zipFileBase64);
});

it("waits for an operation's definition before showing inputs or applying defaults", async () => {
  serveSchema(FORM_SCHEMA, true);
  const view = render(<Harness />);
  expect(
    screen.queryByRole("combobox", { name: /source type/i }),
  ).not.toBeInTheDocument();
  expect(input()).not.toHaveProperty("source");
  serveSchema(FORM_SCHEMA);
  view.rerender(<Harness />);
  await screen.findByRole("combobox", { name: /source type/i });
  await waitFor(() => expect(input().source.sourceType).toBe("BASE64"));
});

it("emits defaults without mutating the editor's original task inputs", async () => {
  serveSchema(DEFAULT_SCHEMA);
  const original = Object.freeze({
    integrationName: "test-integration",
    method: "Create Function",
  });
  const onChange = vi.fn();
  render(
    <ThemeProvider>
      <MemoryRouter>
        <IntegrationTaskForm
          task={{ inputParameters: original }}
          onChange={onChange}
        />
      </MemoryRouter>
    </ThemeProvider>,
  );
  await waitFor(() =>
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: expect.objectContaining({
          useFunctionKey: true,
          retries: 2,
        }),
      }),
    ),
  );
  expect(original).not.toHaveProperty("useFunctionKey");
  expect(original).not.toHaveProperty("retries");
});

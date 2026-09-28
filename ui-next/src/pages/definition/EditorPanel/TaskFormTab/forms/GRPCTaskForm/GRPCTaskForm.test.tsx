/**
 * CCOR-13476: the GRPC form had no control for `compressionCodec`, so it could
 * only be set by hand in the Code tab. The field is free text rather than a
 * fixed list — gzip and identity are the built-ins, but a server can register
 * others — and it has to survive a round trip without disturbing the rest of
 * inputParameters.
 */
import { fireEvent, render, screen } from "@testing-library/react";
import { GrpcTaskDef, TaskType } from "types";
import { GRPCTaskForm } from "./GRPCTaskForm";

vi.mock("@xstate/react", () => ({
  useInterpret: () => ({}),
  useSelector: () => undefined,
  useActor: () => [{ context: {} }, vi.fn()],
}));

vi.mock("../HTTPTaskForm/state/hook", () => ({
  useServiceMethodsDefinition: () => [
    {
      services: [],
      selectedService: undefined,
      selectedServiceMethods: [],
      selectedMethod: undefined,
      schemas: [],
      showServiceRegistryPopulatorModal: false,
      currentTaskDefinition: undefined,
      selectedHost: undefined,
    },
    {
      handleSelectService: vi.fn(),
      handleSelectMethod: vi.fn(),
      handleShowServiceRegistryPopulatorModal: vi.fn(),
      handleChangeTaskDefName: vi.fn(),
      handleSelectHost: vi.fn(),
    },
  ],
}));

vi.mock("utils/query", () => ({
  useAuthHeaders: () => ({ "X-Authorization": "ui-token" }),
}));

// Other modules pulled in transitively read their own flags, so answer for any
// key rather than listing them.
vi.mock("utils/flags", () => ({
  FEATURES: new Proxy({}, { get: (_target, key) => String(key) }),
  featureFlags: { isEnabled: () => false, getValue: () => undefined },
}));

// Every free-text field in this form uses it, so it is keyed by label.
vi.mock("components/FlatMapForm/ConductorAutocompleteVariables", () => ({
  ConductorAutocompleteVariables: ({ id, label, value, onChange }: any) => (
    <input
      data-testid={id}
      aria-label={String(label)}
      value={value ?? ""}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}));

vi.mock("components/ui/inputs", () => ({
  ConductorAutoComplete: ({ label, value }: any) => (
    <div aria-label={String(label)}>{value ?? ""}</div>
  ),
}));

vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: () => null,
}));

vi.mock("../HTTPTaskForm/ConductorAdditionalHeaders", () => ({
  ConductorAdditionalHeaders: () => null,
}));

vi.mock("../HTTPTaskForm/EditTaskDefConfigModal", () => ({
  default: () => null,
}));

vi.mock("../HedgingConfigForm", () => ({ default: () => null }));
vi.mock("../ServiceRegistrySelector", () => ({ default: () => null }));
vi.mock("../ConductorCacheOutputForm", () => ({
  ConductorCacheOutput: () => null,
}));
vi.mock("../OptionalFieldForm", () => ({
  Optional: ({ children }: any) => <>{children}</>,
}));
vi.mock("../TaskFormSection", () => ({
  default: ({ children }: any) => <div>{children}</div>,
}));
vi.mock("../MaybeVariable", () => ({
  MaybeVariable: ({ children }: any) => <div>{children}</div>,
}));

vi.mock("react-router", () => ({
  Link: ({ children }: any) => <>{children}</>,
}));

const CODEC_FIELD = "grpc-task-compression-codec";

const baseTask = (
  inputParameters: GrpcTaskDef["inputParameters"] = {},
): GrpcTaskDef =>
  ({
    name: "grpc_1",
    taskReferenceName: "grpc_ref_1",
    type: TaskType.GRPC,
    inputParameters,
  }) as GrpcTaskDef;

const renderForm = (task: GrpcTaskDef) => {
  const onChange = vi.fn();
  render(<GRPCTaskForm task={task} onChange={onChange} />);
  return onChange;
};

const codecField = () => screen.getByTestId(CODEC_FIELD) as HTMLInputElement;

describe("GRPCTaskForm compression codec", () => {
  it("shows the codec already on the task", () => {
    renderForm(baseTask({ compressionCodec: "gzip" }));

    expect(codecField().value).toBe("gzip");
  });

  it("is empty when the task has no codec", () => {
    renderForm(baseTask());

    expect(codecField().value).toBe("");
  });

  it("writes the typed codec onto inputParameters", () => {
    const onChange = renderForm(baseTask());

    fireEvent.change(codecField(), { target: { value: "gzip" } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: expect.objectContaining({ compressionCodec: "gzip" }),
      }),
    );
  });

  it("accepts a codec the UI does not know about", () => {
    const onChange = renderForm(baseTask());

    // Free text on purpose: a server may register its own codecs.
    fireEvent.change(codecField(), { target: { value: "deflate" } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: expect.objectContaining({
          compressionCodec: "deflate",
        }),
      }),
    );
  });

  it("accepts a workflow variable", () => {
    const onChange = renderForm(baseTask());

    fireEvent.change(codecField(), {
      target: { value: "${workflow.input.codec}" },
    });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: expect.objectContaining({
          compressionCodec: "${workflow.input.codec}",
        }),
      }),
    );
  });

  it("leaves the rest of inputParameters untouched", () => {
    const onChange = renderForm(
      baseTask({
        service: "petstore",
        method: "PetService/GetPet",
        host: "localhost",
        port: 50051,
      }),
    );

    fireEvent.change(codecField(), { target: { value: "gzip" } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: expect.objectContaining({
          service: "petstore",
          method: "PetService/GetPet",
          host: "localhost",
          port: 50051,
          compressionCodec: "gzip",
        }),
      }),
    );
  });

  it("clears back to an empty string rather than dropping the key", () => {
    const onChange = renderForm(baseTask({ compressionCodec: "gzip" }));

    fireEvent.change(codecField(), { target: { value: "" } });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: expect.objectContaining({ compressionCodec: "" }),
      }),
    );
  });
});

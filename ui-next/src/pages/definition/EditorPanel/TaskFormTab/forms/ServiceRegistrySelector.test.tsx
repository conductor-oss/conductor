import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MessageContext } from "components/providers/messageContext";
import type { Method, ServiceDefDto } from "types/RemoteServiceTypes";
import ServiceRegistryPopulator from "./ServiceRegistrySelector";

const mocks = vi.hoisted(() => ({
  isInIdleState: true,
  handleUpdateTemplate: vi.fn(),
}));

vi.mock("./HTTPTaskForm/state/hook", () => ({
  useServiceMethodsDefinition: () => [
    { isInIdleState: mocks.isInIdleState },
    { handleUpdateTemplate: mocks.handleUpdateTemplate },
  ],
}));

vi.mock("utils/remoteServices", () => ({
  replaceDynamicParams: (methodName: string) => ({
    url: methodName ?? "",
    headers: { "X-Test": "1" },
  }),
}));

vi.mock("components/ui/dialogs/UIModal", () => ({
  default: ({ open, children }: any) =>
    open ? <div data-testid="modal">{children}</div> : null,
}));

vi.mock("components/ui/buttons/MuiButton", () => ({
  default: ({ children, onClick }: any) => (
    <button onClick={onClick}>{children}</button>
  ),
}));

vi.mock("components/ui/inputs", () => ({
  ConductorAutoComplete: ({ label }: any) => (
    <div aria-label={label}>{label}</div>
  ),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ label, value }: any) => (
    <div>
      <label>{label}</label>
      <input aria-label={label} value={value ?? ""} readOnly />
    </div>
  ),
}));

vi.mock("components/FlatMapForm/ConductorAutocompleteVariables", () => ({
  ConductorAutocompleteVariables: ({ value, onChange }: any) => (
    <input
      data-testid="param-input"
      value={value ?? ""}
      onChange={(e) => onChange?.(e.target.value)}
    />
  ),
}));

vi.mock("@mui/material", async () => {
  const actual =
    await vi.importActual<typeof import("@mui/material")>("@mui/material");
  return {
    ...actual,
    Tooltip: ({ title, children }: any) => (
      <span
        data-testid="tooltip"
        data-title={typeof title === "string" ? title : ""}
      >
        {children}
      </span>
    ),
  };
});

const baseHttpService = {
  name: "test-service",
  type: "HTTP",
  methods: [],
  serviceURI: "http://api.example.com",
  config: { circuitBreakerConfig: {} },
  servers: [],
} as unknown as ServiceDefDto;

const baseGrpcService = {
  ...baseHttpService,
  type: "gRPC",
  serviceURI: "grpc.example.com:50051",
} as unknown as ServiceDefDto;

const baseMethod: Method = {
  operationName: "doThing",
  methodName: "/v1/do",
  methodType: "GET",
  inputType: "any",
  outputType: "any",
};

const setMessage = vi.fn();

const renderPopulator = (overrides: Record<string, any> = {}) => {
  const props = {
    modalShow: true,
    setModalShow: vi.fn(),
    handleSelectService: vi.fn(),
    handleSelectHost: vi.fn(),
    handleSelectMethod: vi.fn(),
    selectedService: baseHttpService,
    services: [baseHttpService],
    selectedMethod: baseMethod,
    selectedServiceMethodsOptions: [] as any,
    serviceType: "HTTP",
    actor: {} as any,
    selectedHost: "",
    ...overrides,
  };
  return render(
    <MessageContext.Provider value={{ setMessage }}>
      <ServiceRegistryPopulator {...props} />
    </MessageContext.Provider>,
  );
};

const getModal = () => screen.getByTestId("modal");
const getPopulateButton = () => within(getModal()).getByRole("button");
const getTooltipTitle = () =>
  within(getModal()).getByTestId("tooltip").getAttribute("data-title");

beforeEach(() => {
  mocks.isInIdleState = true;
  mocks.handleUpdateTemplate.mockClear();
  setMessage.mockClear();
});

describe("ServiceRegistryPopulator — Populate button gating", () => {
  describe("disabled states", () => {
    it("prompts to select a service when none is selected", () => {
      renderPopulator({ selectedService: undefined });
      expect(getPopulateButton()).toBeDisabled();
      expect(getTooltipTitle()).toBe("Select a service");
    });

    it("prompts for a host (HTTP) when serviceURI and selectedHost are both missing", () => {
      renderPopulator({
        selectedService: { ...baseHttpService, serviceURI: "" },
        selectedHost: "",
      });
      expect(getPopulateButton()).toBeDisabled();
      expect(getTooltipTitle()).toBe("Select a host");
    });

    it("prompts for a host:port (gRPC) when serviceURI and selectedHost are both missing", () => {
      renderPopulator({
        selectedService: { ...baseGrpcService, serviceURI: "" },
        selectedHost: "",
        serviceType: "gRPC",
      });
      expect(getPopulateButton()).toBeDisabled();
      expect(getTooltipTitle()).toBe("Select a host:port");
    });

    it("prompts for a method when service and host are set but method is missing", () => {
      renderPopulator({
        selectedMethod: { ...baseMethod, methodType: undefined },
      });
      expect(getPopulateButton()).toBeDisabled();
      expect(getTooltipTitle()).toBe("Select a service method");
    });

    it("prompts for params when HTTP method has unfilled required params", () => {
      renderPopulator({
        selectedMethod: {
          ...baseMethod,
          requestParams: [{ name: "id", type: "PATH", required: true }],
        },
      });
      expect(getPopulateButton()).toBeDisabled();
      expect(getTooltipTitle()).toBe("Fill all required parameters");
    });

    it("ignores params validity for gRPC (no request params table for gRPC)", () => {
      renderPopulator({
        selectedService: baseGrpcService,
        serviceType: "gRPC",
        selectedMethod: {
          ...baseMethod,
          methodType: "UNARY",
          requestParams: [{ name: "id", type: "PATH", required: true }],
        },
      });
      expect(getPopulateButton()).not.toBeDisabled();
    });

    it("disables with no tooltip text while populate is in-flight", () => {
      mocks.isInIdleState = false;
      renderPopulator();
      expect(getPopulateButton()).toBeDisabled();
      expect(getTooltipTitle()).toBe("");
    });
  });

  describe("enabled states", () => {
    it("enables when only serviceURI is present (no selectedHost)", () => {
      renderPopulator({ selectedHost: "" });
      expect(getPopulateButton()).not.toBeDisabled();
      expect(getTooltipTitle()).toBe("");
    });

    it("enables when only selectedHost is present (no serviceURI) — original bug case", () => {
      renderPopulator({
        selectedService: { ...baseHttpService, serviceURI: "" },
        selectedHost: "http://override.example.com",
      });
      expect(getPopulateButton()).not.toBeDisabled();
      expect(getTooltipTitle()).toBe("");
    });
  });

  describe("populate side effects", () => {
    it("HTTP: handleUpdateTemplate receives the dynamic-params URL and a success toast fires", () => {
      const setModalShow = vi.fn();
      renderPopulator({
        setModalShow,
        selectedService: { ...baseHttpService, serviceURI: "" },
        selectedHost: "http://override.example.com",
      });

      fireEvent.click(getPopulateButton());

      expect(mocks.handleUpdateTemplate).toHaveBeenCalledTimes(1);
      expect(mocks.handleUpdateTemplate).toHaveBeenCalledWith({
        updatedUrl: baseMethod.methodName,
        headers: { "X-Test": "1" },
      });
      expect(setModalShow).toHaveBeenCalledWith(false);
      expect(setMessage).toHaveBeenCalledWith({
        severity: "success",
        text: "Applied successfully",
      });
    });

    it("gRPC: handleUpdateTemplate receives updatedUrl=selectedHost", () => {
      renderPopulator({
        selectedService: { ...baseGrpcService, serviceURI: "" },
        selectedHost: "grpc.override.example.com:1234",
        serviceType: "gRPC",
        selectedMethod: { ...baseMethod, methodType: "UNARY" },
      });

      fireEvent.click(getPopulateButton());

      expect(mocks.handleUpdateTemplate).toHaveBeenCalledWith({
        updatedUrl: "grpc.override.example.com:1234",
        headers: { "X-Test": "1" },
      });
    });

    it("merges authMetadata headers into the populate payload", () => {
      renderPopulator({
        selectedService: {
          ...baseHttpService,
          authMetadata: { key: "Authorization", value: "Bearer abc" },
        },
      });

      fireEvent.click(getPopulateButton());

      expect(mocks.handleUpdateTemplate).toHaveBeenCalledWith({
        updatedUrl: baseMethod.methodName,
        headers: {
          "X-Test": "1",
          Authorization: "Bearer abc",
        },
      });
    });

    it("does nothing when prerequisites are missing", () => {
      renderPopulator({ selectedService: undefined });
      // Button is disabled, but assert the side-effect contract directly in case
      // the disabled state ever regresses.
      fireEvent.click(getPopulateButton());
      expect(mocks.handleUpdateTemplate).not.toHaveBeenCalled();
      expect(setMessage).not.toHaveBeenCalled();
    });
  });
});

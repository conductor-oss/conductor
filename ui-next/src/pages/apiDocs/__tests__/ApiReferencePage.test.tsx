import { render } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ApiReferencePage from "../ApiReferencePage";

describe("ApiReferencePage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("replaces location with swagger url on mount", () => {
    const replaceMock = vi.fn();
    Object.defineProperty(window, "location", {
      configurable: true,
      value: {
        host: "localhost:5000",
        replace: replaceMock,
      },
    });

    render(<ApiReferencePage />);

    expect(replaceMock).toHaveBeenCalledWith(
      "//localhost:5000/swagger-ui/index.html?configUrl=/api-docs/swagger-config#/",
    );
  });
});

import { urlWithQueryParameters } from "../toMaybeQueryString";

describe("urlWithQueryParameters", () => {
  it("should append query parameters with ? when URL has no existing parameters", () => {
    const url = "https://example.com/api";
    const params = { key: "value", another: "param" };
    expect(urlWithQueryParameters(url, params)).toBe(
      "https://example.com/api?key=value&another=param",
    );
  });

  it("should append query parameters with & when URL already has parameters", () => {
    const url = "https://example.com/api?existing=true";
    const params = { key: "value", another: "param" };
    expect(urlWithQueryParameters(url, params)).toBe(
      "https://example.com/api?existing=true&key=value&another=param",
    );
  });

  it("should handle empty parameters object", () => {
    const url = "https://example.com/api";
    const params = {};
    expect(urlWithQueryParameters(url, params)).toBe("https://example.com/api");
  });

  it("should handle undefined values in parameters", () => {
    const url = "https://example.com/api";
    const params = { key: "value", empty: undefined };
    expect(urlWithQueryParameters(url, params)).toBe(
      "https://example.com/api?key=value",
    );
  });
  it("should handle empty url", () => {
    const url = "";
    const params = {};
    expect(urlWithQueryParameters(url, params)).toBe("");
  });
});

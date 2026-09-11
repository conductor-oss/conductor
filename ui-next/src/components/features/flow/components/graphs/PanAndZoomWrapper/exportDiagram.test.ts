import domToImage from "dom-to-image";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  describeExportFailure,
  encodeSvgPayload,
  exportFailureMessage,
  printScreen,
} from "./exportDiagram";

// The browser percent-decodes a data: URI payload when it loads it, so whatever
// dom-to-image writes into the URI has to decode back to the exact markup. The
// library's own escaping only covers "#" and newlines, which silently corrupts or
// outright breaks anything else.
describe("encodeSvgPayload", () => {
  const cases = [
    ["a bare percent", "<div>progress 100% done</div>"],
    ["a percent-encoded angle bracket", "<div>discount%3Cvalue</div>"],
    ["a percent-encoded letter", "<div>batch%41size</div>"],
    ["a fragment reference", '<use filter="url(#shadow-1)"></use>'],
    ["a newline", "<div>first\nsecond</div>"],
    ["a tab and carriage return", "<div>col1\tcol2\r\n</div>"],
    ["non-ascii text", "<div>café → naïve</div>"],
  ] as const;

  it.each(cases)("round-trips markup containing %s", (_name, markup) => {
    expect(decodeURIComponent(encodeSvgPayload(markup))).toBe(markup);
  });

  it("is installed on dom-to-image so generated URIs are encoded", () => {
    const util = (
      domToImage as unknown as {
        impl: { util: { escapeXhtml: (markup: string) => string } };
      }
    ).impl.util;

    expect(util.escapeXhtml("a%3Cb")).toBe(encodeSvgPayload("a%3Cb"));
  });
});

describe("describeExportFailure", () => {
  it("names the failing stage for an image error event", () => {
    expect(describeExportFailure(new Event("error"))).toBe(
      "the generated SVG could not be loaded as an image",
    );
  });

  it("uses the message of a thrown error", () => {
    const error = new Error("Tainted canvases may not be exported.");
    error.name = "SecurityError";

    expect(describeExportFailure(error)).toBe(
      "Tainted canvases may not be exported.",
    );
  });

  it("falls back to the error name when there is no message", () => {
    expect(describeExportFailure(new DOMException("", "SecurityError"))).toBe(
      "SecurityError",
    );
  });

  it("passes through a string rejection", () => {
    expect(describeExportFailure("cannot fetch resource: /a.svg")).toBe(
      "cannot fetch resource: /a.svg",
    );
  });

  it("avoids surfacing an unreadable object", () => {
    expect(describeExportFailure({ nope: true })).toBe("unknown error");
  });
});

describe("printScreen", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = "";
  });

  const mountDiagram = () => {
    document.body.innerHTML =
      '<div id="diagram-canvas-container"><div>diagram</div></div>';
  };

  it("reports why the export failed", async () => {
    mountDiagram();
    vi.spyOn(console, "error").mockImplementation(() => {});
    // The real rejection is an image error event, which carries no message.
    const rejection = new Event("error");
    vi.spyOn(domToImage, "toPng").mockRejectedValue(rejection);
    const onFailure = vi.fn();

    printScreen("switch", onFailure);
    await vi.waitFor(() => expect(onFailure).toHaveBeenCalledOnce());

    expect(onFailure).toHaveBeenCalledWith(exportFailureMessage(rejection));
    expect(onFailure).toHaveBeenCalledWith(
      expect.stringContaining("the generated SVG could not be loaded"),
    );
  });

  it("does not report a failure when the export succeeds", async () => {
    mountDiagram();
    vi.spyOn(domToImage, "toPng").mockResolvedValue(
      "data:image/png;base64,AAA",
    );
    const onFailure = vi.fn();
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});

    printScreen("switch", onFailure);
    await vi.waitFor(() => expect(click).toHaveBeenCalledOnce());

    expect(onFailure).not.toHaveBeenCalled();
  });
});

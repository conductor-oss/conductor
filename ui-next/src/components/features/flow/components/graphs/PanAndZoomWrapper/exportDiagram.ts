import domToImage from "dom-to-image";

/**
 * dom-to-image serialises the cloned diagram into an `<svg><foreignObject>` payload
 * and hands it to an `<img>` as a `data:` URI, but it only escapes `#` and newlines
 * on the way. Every other character is left raw, so the browser percent-decodes the
 * payload a second time while loading the URI. Markup containing a sequence such as
 * `%3C` therefore arrives as a bare `<`, the SVG stops parsing, and the export
 * rejects with nothing but an image `error` event. Tabs and carriage returns are a
 * second variant of the same problem: the URL parser strips them outright.
 *
 * Encoding the whole payload keeps the round-trip lossless, and matches what the
 * maintained fork of this library (html-to-image) does.
 */
export const encodeSvgPayload = (markup: string) => encodeURIComponent(markup);

// `impl` is an intentional escape hatch in dom-to-image but is absent from its
// type definitions. Keep it optional so a future upgrade degrades to the library's
// own behaviour rather than throwing.
type DomToImageInternals = {
  impl?: { util?: { escapeXhtml?: (markup: string) => string } };
};

export const applySvgPayloadEncodingFix = () => {
  const util = (domToImage as unknown as DomToImageInternals).impl?.util;
  if (util) util.escapeXhtml = encodeSvgPayload;
};

applySvgPayloadEncodingFix();

export const describeExportFailure = (error: unknown) => {
  // dom-to-image rejects with the <img> element's own error event when the SVG it
  // built cannot be loaded, and an Event carries no message at all: it stringifies
  // to "[object Event]". Name the stage that failed instead.
  if (error instanceof Event) {
    return "the generated SVG could not be loaded as an image";
  }
  if (error instanceof Error) {
    return error.message || error.name;
  }
  const described = String(error);
  return described === "[object Object]" ? "unknown error" : described;
};

export const exportFailureMessage = (error: unknown) =>
  `Could not export the diagram to an image: ${describeExportFailure(error)}`;

export const printScreen = (
  workflowName: string,
  onFailure?: (message: string) => void,
) => {
  const node = document.getElementById("diagram-canvas-container");

  if (!node?.firstChild) return;

  domToImage
    .toPng(node.firstChild)
    .then(function (dataUrl: string) {
      const link = document.createElement("a");
      link.download = `${workflowName}.png`;
      link.href = dataUrl;
      link.click();
    })
    .catch(function (error: unknown) {
      const message = exportFailureMessage(error);
      console.error(message, error);
      onFailure?.(message);
    });
};

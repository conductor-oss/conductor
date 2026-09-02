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
export declare const encodeSvgPayload: (markup: string) => string;
export declare const applySvgPayloadEncodingFix: () => void;
export declare const describeExportFailure: (error: unknown) => string;
export declare const exportFailureMessage: (error: unknown) => string;
export declare const printScreen: (workflowName: string, onFailure?: (message: string) => void) => void;

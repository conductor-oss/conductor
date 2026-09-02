/** Monaco JSON viewer. Fills its container, so give it a sized parent. */
export declare function JsonView({ src }: {
    src: unknown;
}): import("react").JSX.Element;
/** Raw text or JSON, as-is. `maxHeight` makes tall content scroll instead of grow. */
export declare function PreformattedText({ text, maxHeight, }: {
    text: string;
    maxHeight?: number;
}): import("react").JSX.Element;
export declare function MarkdownView({ content }: {
    content: string;
}): import("react").JSX.Element;
/** Renders any value: Markdown, preformatted text, or a JSON viewer. */
export declare function ContentView({ value, label, }: {
    value: unknown;
    label?: string;
}): import("react").JSX.Element;

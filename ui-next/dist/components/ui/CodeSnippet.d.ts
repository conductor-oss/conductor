type CodeSnippetProps = {
    code: string;
    className?: string;
    noCopyToClipboard?: boolean;
    variant?: "default" | "guide";
    sx?: any;
};
export declare const CodeSnippet: ({ code, className, noCopyToClipboard, variant, sx, }: CodeSnippetProps) => import("react").JSX.Element;
export {};

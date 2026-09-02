import React from "react";
interface DocLinkProps {
    url: string;
    label: string;
    position?: "relative" | "absolute";
    right?: string;
    top?: string;
}
export declare const DocLink: ({ url, label, position, right, top, }: DocLinkProps) => React.JSX.Element;
export {};

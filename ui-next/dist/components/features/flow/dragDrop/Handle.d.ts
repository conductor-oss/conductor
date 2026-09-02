import React, { CSSProperties } from "react";
export interface ActionProps extends React.HTMLAttributes<HTMLButtonElement> {
    active?: {
        fill: string;
        background: string;
    };
    cursor?: CSSProperties["cursor"];
}
export declare const Action: React.ForwardRefExoticComponent<ActionProps & React.RefAttributes<HTMLButtonElement>>;
export declare const Handle: React.ForwardRefExoticComponent<ActionProps & React.RefAttributes<HTMLButtonElement>>;

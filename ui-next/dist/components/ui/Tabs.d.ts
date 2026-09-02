import type { TabProps } from "@mui/material/Tab";
import type { TabsProps } from "@mui/material/Tabs";
import React from "react";
export type TabsOwnProps = TabsProps & {
    contextual?: boolean;
};
export default function Tabs({ contextual, children, ...props }: TabsOwnProps): React.JSX.Element;
export type TabOwnProps = TabProps & {
    contextual?: boolean | null;
};
export declare function Tab({ contextual, ...props }: TabOwnProps): React.JSX.Element;

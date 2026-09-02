/**
 * BaseLayout — the OSS sidebar + top bar layout with no enterprise dependencies.
 *
 * The enterprise `additional` plugin replaces this with an agent-aware wrapper
 * by registering an `appLayout` component via the plugin registry.
 */
import { ReactNode } from "react";
type Props = {
    children: ReactNode;
};
export declare const BaseLayout: ({ children }: Props) => import("react").JSX.Element;
export default BaseLayout;

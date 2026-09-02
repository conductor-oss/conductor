import { ReactNode } from "react";
export interface BlockNavigationWithConfirmationProps {
    nonBlockPaths: string[];
    promptMessage?: ReactNode;
    title?: ReactNode;
    block?: boolean;
    hasErrors?: boolean;
    onSave?: () => void;
    successfulSave?: boolean;
    onDiscard?: () => void;
}
declare const BlockNavigationWithConfirmation: ({ nonBlockPaths, block, title, hasErrors, onSave, successfulSave, onDiscard, }: BlockNavigationWithConfirmationProps) => import("react").JSX.Element;
export default BlockNavigationWithConfirmation;

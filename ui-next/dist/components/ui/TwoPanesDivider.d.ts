import { type Dispatch, type ReactNode, type SetStateAction } from "react";
export type TwoPanesDividerProps = {
    leftPanelContent: ReactNode;
    rightPanelContent: ReactNode;
    leftPanelExpanded?: boolean;
    setLeftPanelExpanded: Dispatch<SetStateAction<boolean>>;
    hideCollapseButton?: boolean;
};
declare const TwoPanesDivider: ({ leftPanelContent, rightPanelContent, leftPanelExpanded, setLeftPanelExpanded, hideCollapseButton: _hideCollapseButton, }: TwoPanesDividerProps) => import("react").JSX.Element;
export default TwoPanesDivider;

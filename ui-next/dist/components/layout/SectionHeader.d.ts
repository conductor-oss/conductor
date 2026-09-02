import { ReactNode } from "react";
interface SectionHeaderProps {
    title: string;
    actions?: ReactNode;
    _deprecate_marginTop?: number;
    marginRightForAction?: number;
}
declare const SectionHeader: ({ title, actions, _deprecate_marginTop, marginRightForAction, }: SectionHeaderProps) => import("react").JSX.Element;
export default SectionHeader;

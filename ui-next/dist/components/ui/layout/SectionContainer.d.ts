import { ReactNode } from "react";
export type SectionContainerProps = {
    children?: ReactNode;
    header?: ReactNode;
    featureDisabledCustomComponent?: ReactNode;
};
declare const SectionContainer: ({ children, header, featureDisabledCustomComponent, }: SectionContainerProps) => import("react").JSX.Element;
export default SectionContainer;

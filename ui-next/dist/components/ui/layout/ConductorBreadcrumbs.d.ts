import { BreadcrumbsProps } from "@mui/material";
type ConductorBreadcrumbsProps = BreadcrumbsProps & {
    items: any;
    color?: string;
};
declare const ConductorBreadcrumbs: ({ items, color, ...rest }: ConductorBreadcrumbsProps) => import("react").JSX.Element;
export type { ConductorBreadcrumbsProps };
export default ConductorBreadcrumbs;

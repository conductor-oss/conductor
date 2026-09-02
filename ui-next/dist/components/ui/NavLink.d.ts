import { CSSProperties, ReactNode } from "react";
interface NavLinkProps {
    path: string;
    newTab?: boolean;
    children: ReactNode;
    id?: string;
    style?: CSSProperties;
    target?: string;
    color?: string;
}
declare const NavLink: import("react").ForwardRefExoticComponent<NavLinkProps & import("react").RefAttributes<any>>;
export default NavLink;

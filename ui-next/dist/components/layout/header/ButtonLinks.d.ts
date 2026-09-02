import { BoxProps } from "@mui/material/Box";
export interface ButtonLinksProps extends BoxProps {
    showDropdownOnly: boolean;
    isSideBarOpen: boolean;
}
export default function ButtonLinks({ showDropdownOnly, isSideBarOpen, ...rest }: ButtonLinksProps): import("react").JSX.Element | null;

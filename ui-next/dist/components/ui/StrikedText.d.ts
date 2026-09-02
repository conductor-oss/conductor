import { CSSProperties, ReactNode } from "react";
interface StrikedTextProps {
    children: ReactNode;
    sx?: CSSProperties;
}
declare const StrikedText: ({ children, sx, ...props }: StrikedTextProps) => import("react").JSX.Element;
export default StrikedText;

import { CSSProperties } from "react";
export interface ReactJSONProps {
    src: any;
    title?: string;
    className?: string;
    style?: CSSProperties;
    showIconText?: boolean;
    workflowName?: string;
    editorHeight?: string;
    item?: any;
    handleFullScreen?: (item: any) => void;
    fullScreen?: any;
    customOptions?: object;
    overflowX?: string;
    overflowY?: string;
    isEditable?: boolean;
    handleUpdate?: (value: string) => void;
}
export default function ReactJson({ title, className, style, showIconText, editorHeight, handleFullScreen, item, fullScreen, customOptions, overflowX, overflowY, isEditable, handleUpdate, ...props }: ReactJSONProps): import("react").JSX.Element;

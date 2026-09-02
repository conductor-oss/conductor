import { CSSProperties } from "react";
type DataType = {
    title: string;
    src: Record<string, unknown>;
    hidden: boolean;
    style: CSSProperties;
};
interface InputOutputProp {
    data: DataType[];
    execution: Record<string, unknown>;
    isEditable?: boolean;
    handleUpdate?: (value: string) => void;
}
export default function InputOutput({ data, execution, isEditable, handleUpdate, }: InputOutputProp): import("react").JSX.Element;
export {};

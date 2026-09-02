import { type ReactNode } from "react";
export type KeyValueTableRow = {
    label: ReactNode;
    value: unknown;
    type?: string;
};
type KeyValueTableProps = {
    data: KeyValueTableRow[];
};
export default function KeyValueTable({ data }: KeyValueTableProps): import("react").JSX.Element;
export {};

import { ReactNode } from "react";
export interface CompoundFilterProps {
    label: string;
    active: boolean;
    operator: ReactNode;
    value: ReactNode;
}
export declare const CompoundFilter: ({ label, active, operator, value, }: CompoundFilterProps) => import("react").JSX.Element;

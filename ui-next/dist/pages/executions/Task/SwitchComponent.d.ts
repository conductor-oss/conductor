import { SetStateAction } from "react";
import { QueryDispatch } from "react-router-use-location-state";
interface SwitchComponentProps {
    asQuery: boolean;
    setAsQuery: QueryDispatch<SetStateAction<boolean>>;
}
export declare const SwitchComponent: ({ asQuery, setAsQuery, }: SwitchComponentProps) => import("react").JSX.Element;
export {};

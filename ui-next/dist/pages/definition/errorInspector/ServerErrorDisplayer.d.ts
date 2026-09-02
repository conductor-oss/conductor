import { FunctionComponent } from "react";
import { ValidationError } from "./state/types";
import { TaskDef } from "types/common";
interface ServerErrorsDisplayerProps {
    serverErrors: ValidationError[];
    onCleanServerError: () => void;
    onClickReference?: (data: string) => void;
    tasks?: TaskDef[];
}
export declare const ServerErrorsDisplayer: FunctionComponent<ServerErrorsDisplayerProps>;
export {};

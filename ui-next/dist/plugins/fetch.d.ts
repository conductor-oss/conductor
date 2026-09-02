import { IObject } from "types/common";
export declare function fetchContextNonHook(): {
    stack: string;
    ready: boolean;
};
export declare function useFetchContext(): {
    setMessage: (msg: import("..").PopoverMessage | null) => void;
    stack: string;
    ready: boolean;
};
export declare function fetchWithContext(path: string, context: IObject, fetchParams: IObject, isText?: boolean, throwOnError?: boolean): Promise<any>;

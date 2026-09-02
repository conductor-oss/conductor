import { ChangeEvent } from "react";
/**
 * If chars are valid, call handler
 * @param handler
 * @param regExVal
 * @returns
 */
export declare const handleValidChars: (handler: (val: string) => void, regExVal?: string) => (value: string) => void;
export declare const handleValidCharsForEvents: (handler: (evt: ChangeEvent<HTMLInputElement>) => void, regExVal?: string) => (ov: ChangeEvent<HTMLInputElement>) => void;

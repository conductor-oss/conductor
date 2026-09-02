import { SupportedDisplayTypes } from "./types";
export type toCodeT<T> = Partial<Record<SupportedDisplayTypes, (args: T, accessToken: string) => string>>;
export declare const useParamsToSdk: <T>(args: T, toCode: toCodeT<T>) => {
    selectedLanguage: SupportedDisplayTypes;
    setSelectedLanguage: import("react").Dispatch<import("react").SetStateAction<SupportedDisplayTypes>>;
    code: string;
};

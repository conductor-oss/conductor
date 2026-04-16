import { useState } from "react";
import { SupportedDisplayTypes } from "./types";
import _prop from "lodash/property";
import { getAccessToken } from "components/features/auth/tokenManagerJotai";

export type toCodeT<T> = Partial<
  Record<SupportedDisplayTypes, (args: T, accessToken: string) => string>
>;

export const useParamsToSdk = <T>(args: T, toCode: toCodeT<T>) => {
  const [selectedLanguage, setSelectedLanguage] =
    useState<SupportedDisplayTypes>("curl");

  const toCodeFunc = _prop<
    toCodeT<T>,
    (args: T, accessToken: string) => string
  >(selectedLanguage)(toCode);

  const accessToken = getAccessToken() || "";

  return {
    selectedLanguage,
    setSelectedLanguage,
    code: toCodeFunc(args, accessToken),
  };
};

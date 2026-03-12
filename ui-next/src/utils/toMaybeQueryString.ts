import _isEmpty from "lodash/isEmpty";
import _pickBy from "lodash/pickBy";
import _isNil from "lodash/isNil";

export type UrlOptions =
  | string
  | string[][]
  | Record<string, any>
  | URLSearchParams
  | undefined;

export const toMaybeQueryString = (
  qOptions: UrlOptions,
  prefixChar: "?" | "&" = "?",
): string => {
  const cleanedObject = _pickBy(
    qOptions as object,
    (a) => !_isNil(a),
  ) as UrlOptions;
  return _isEmpty(qOptions)
    ? ""
    : `${prefixChar}${
        new URLSearchParams(cleanedObject).toString() // filter out undefined values
      }`;
};

export const urlWithQueryParameters = (
  url: string,
  qOptions: UrlOptions,
): string => {
  try {
    const hasParams = [...new URL(url).searchParams]?.length;
    return url + toMaybeQueryString(qOptions, hasParams ? "&" : "?");
  } catch {
    return url + toMaybeQueryString(qOptions, "?");
  }
};

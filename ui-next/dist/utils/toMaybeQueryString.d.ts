export type UrlOptions = string | string[][] | Record<string, any> | URLSearchParams | undefined;
export declare const toMaybeQueryString: (qOptions: UrlOptions, prefixChar?: "?" | "&") => string;
export declare const urlWithQueryParameters: (url: string, qOptions: UrlOptions) => string;

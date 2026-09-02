/**
 * Utility functions for remote service operations.
 * Extracted from pages/remoteServices so OSS code can use them without
 * importing from an enterprise page.
 */
export declare function splitHostAndPort(url?: string): {
    host: string;
    port: number | null;
};
export declare function replaceDynamicParams(url: string, params: Record<string, Record<string, unknown>>): {
    url: string;
    headers?: Record<string, string>;
};

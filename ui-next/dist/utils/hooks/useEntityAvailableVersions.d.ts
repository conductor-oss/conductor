type useEntityAvailableVersionsProps = {
    url: string;
    name: string;
    queryKey?: string[];
};
export declare const useEntityAvailableVersions: ({ url, name, }: useEntityAvailableVersionsProps) => {
    availableVersions: number[];
    refetchAvailableVersions: <TPageData>(options?: (import("react-query").RefetchOptions & import("react-query").RefetchQueryFilters<TPageData>) | undefined) => Promise<import("react-query").QueryObserverResult<any, any>>;
    isFetchingAvailableVersions: boolean;
};
export {};

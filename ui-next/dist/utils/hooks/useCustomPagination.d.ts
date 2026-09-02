declare const useCustomPagination: () => readonly [{
    readonly filterParam: string;
    readonly pageParam: string;
    readonly searchParam: string;
}, {
    readonly handlePageChange: (currentTablePage: number) => void;
    readonly handleSearchTermChange: (searchTerm: string) => void;
    readonly setFilterParam: import("react-router-use-location-state").QueryDispatch<import("react-router-use-location-state").SetStateAction<string>>;
    readonly setPageParam: import("react-router-use-location-state").QueryDispatch<import("react-router-use-location-state").SetStateAction<string>>;
    readonly setSearchParam: import("react-router-use-location-state").QueryDispatch<import("react-router-use-location-state").SetStateAction<string>>;
}];
export default useCustomPagination;

import { ReactElement } from "react";
type SearchResultBase = {
    icon?: ReactElement;
    title: string;
    route?: string;
};
type SearchResultRoute = SearchResultBase & {
    sub?: never;
};
type SearchResultSub = SearchResultBase & {
    sub: SearchResults;
};
type SearchResultItem = SearchResultRoute | SearchResultSub;
type SearchResults = Array<SearchResultItem>;
export interface SearchEverythingProps {
    onChange: (change: string, max?: number) => void;
    searchResults?: SearchResults;
    onClear: () => void;
    searchTerm: string;
    setOpen?: (value: boolean) => void;
    maxSearchResults?: number;
}
declare function SearchEverything({ onChange, searchResults, onClear, searchTerm, setOpen, maxSearchResults, }: SearchEverythingProps): import("react").JSX.Element;
export default SearchEverything;

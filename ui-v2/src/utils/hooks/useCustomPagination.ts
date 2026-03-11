import { useCallback } from "react";
import { useQueryState } from "react-router-use-location-state";
import {
  FILTER_QUERY_PARAM,
  PAGE_QUERY_PARAM,
  SEARCH_QUERY_PARAM,
} from "utils/constants/common";

const useCustomPagination = () => {
  const [filterParam, setFilterParam] = useQueryState(FILTER_QUERY_PARAM, "");
  const [pageParam, setPageParam] = useQueryState(PAGE_QUERY_PARAM, "");
  const [searchParam, setSearchParam] = useQueryState(SEARCH_QUERY_PARAM, "");

  const handleSearchTermChange = useCallback(
    (searchTerm: string) => {
      setSearchParam(searchTerm);
    },
    [setSearchParam],
  );

  const handlePageChange = useCallback(
    (currentTablePage: number) => {
      setPageParam(currentTablePage.toString());
    },
    [setPageParam],
  );

  return [
    {
      filterParam,
      pageParam,
      searchParam,
    },
    {
      handlePageChange,
      handleSearchTermChange,
      setFilterParam,
      setPageParam,
      setSearchParam,
    },
  ] as const;
};

export default useCustomPagination;

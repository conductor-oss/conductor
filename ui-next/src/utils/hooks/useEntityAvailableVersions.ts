import { useMemo } from "react";
import _ from "lodash";
import { useFetch } from "utils";

type useEntityAvailableVersionsProps = {
  url: string;
  name: string;
  queryKey?: string[];
};

export const useEntityAvailableVersions = ({
  url,
  name,
}: useEntityAvailableVersionsProps) => {
  const { data, refetch, isFetching } = useFetch(url);

  const availableVersions: number[] = useMemo(() => {
    return (
      _.chain(data)
        .groupBy("name")
        .map((group, key) => ({
          name: key,
          versions: _.map(group, "version"),
        }))
        .find((item) => item.name === name)
        .get("versions")
        .orderBy((version) => version, "asc")
        .value() || []
    );
  }, [data, name]);

  return {
    availableVersions,
    refetchAvailableVersions: refetch,
    isFetchingAvailableVersions: isFetching,
  };
};

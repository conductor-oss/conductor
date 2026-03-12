import { useMemo } from "react";
import { useFetch } from "utils";

type useGetEntitesProps<T, U> = {
  url: string;
  map?: (entities: T[]) => U[];
};

export const useGetEntites = <T, U>({ url, map }: useGetEntitesProps<T, U>) => {
  const { data } = useFetch(url);

  const entities: U[] = useMemo(() => {
    if (!data) {
      return [];
    }

    return map ? map(data) : data;
  }, [data, map]);

  return {
    entities,
  };
};

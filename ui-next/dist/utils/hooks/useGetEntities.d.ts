type useGetEntitesProps<T, U> = {
    url: string;
    map?: (entities: T[]) => U[];
};
export declare const useGetEntites: <T, U>({ url, map }: useGetEntitesProps<T, U>) => {
    entities: U[];
};
export {};

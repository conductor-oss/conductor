export declare const RefreshEvent: ({ refreshInterval, isFetching, elapsed, handleRefresh, changeRefreshRate, }: {
    refreshInterval: number;
    isFetching: boolean;
    elapsed: number;
    handleRefresh: () => void;
    changeRefreshRate: (val: number) => void;
}) => import("react").JSX.Element;

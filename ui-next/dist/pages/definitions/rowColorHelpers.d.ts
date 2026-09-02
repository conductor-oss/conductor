type RowWithActive = {
    active?: boolean;
};
export declare const activeFilterGroups: {
    title: string;
    value: string;
}[];
export declare const pausedrowColor = "#949494";
export declare const pausedLinkColor = "#619bd5";
export declare const activeLinkColor = "#1976d2";
export declare const getLinkColor: (rec: RowWithActive) => "#1976d2" | "#619bd5";
export declare const conditionalRowStyles: {
    when: (row: RowWithActive) => boolean;
    cl: string;
    style: {
        color: string;
    };
}[];
export {};

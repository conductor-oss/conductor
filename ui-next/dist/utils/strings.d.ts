export declare const randomChars: (n?: number) => string;
export declare const getSequentiallySuffix: ({ name, refNames, }: {
    name: string;
    refNames: string[];
}) => {
    name: string;
    taskReferenceName: string;
};
export declare const toUpperFirst: (str: string) => Capitalize<string>;
export declare function asciiSafeJson(json: string): string;

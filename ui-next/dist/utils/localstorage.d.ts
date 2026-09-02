export declare function useLocalStorage(key: string, initialValue: unknown, c?: {
    parse: (text: string, reviver?: (this: any, key: string, value: any) => any) => any;
    code: {
        (value: any, replacer?: (this: any, key: string, value: any) => any, space?: string | number): string;
        (value: any, replacer?: (number | string)[] | null, space?: string | number): string;
    };
}): any[];

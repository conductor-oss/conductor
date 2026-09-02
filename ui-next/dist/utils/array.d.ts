export declare const adjust: <T>(idx: number, aplFun: () => T, sourceArray: T[]) => never[] & T[] & {
    [x: number]: T;
};
/**
 * Takes an index and a count removes from index count elements of array
 *
 * @param {*} idx
 * @param {*} count
 * @param {*} sourceArray
 * @returns
 */
export declare const remove: (idx: number, count: number, sourceArray: Array<any>) => any[];
export declare const insert: <T>(index: number, newItem: T, arr: T[]) => T[];
export declare const cartesianProduct: <TA, TB>(a: Array<TA>, b: Array<TB>) => Array<[TA, TB]>;

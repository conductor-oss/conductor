export const adjust = <T>(idx: number, aplFun: () => T, sourceArray: T[]) =>
  Object.assign([], sourceArray, { [idx]: aplFun() });

/**
 * Takes an index and a count removes from index count elements of array
 *
 * @param {*} idx
 * @param {*} count
 * @param {*} sourceArray
 * @returns
 */
export const remove = (idx: number, count: number, sourceArray: Array<any>) => {
  const arrayCopy = sourceArray.slice();
  arrayCopy.splice(idx, count);
  return arrayCopy;
};

export const insert = <T>(index: number, newItem: T, arr: T[]) =>
  arr.slice(0, index).concat(newItem).concat(arr.slice(index));

export const cartesianProduct = <TA, TB>(
  a: Array<TA>,
  b: Array<TB>,
): Array<[TA, TB]> => a.flatMap((va) => b.map((vb): [TA, TB] => [va, vb]));

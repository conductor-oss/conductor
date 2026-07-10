/**
 * Computes the next query string after setting/removing a single param.
 *
 * Returns `null` when the param would not actually change, signalling the
 * caller to skip navigation (a no-op write would otherwise rebuild a stale url
 * and revert the current navigation). When a value is falsy the key is removed.
 */
export function nextQueryString(
  search: string,
  key: string,
  value: string | undefined,
): string | null {
  const params = new URLSearchParams(search);
  const current = params.toString();
  if (value) {
    params.set(key, value);
  } else {
    params.delete(key);
  }
  const next = params.toString();
  return next === current ? null : next;
}

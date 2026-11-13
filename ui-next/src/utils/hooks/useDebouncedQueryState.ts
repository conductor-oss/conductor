import _debounce from "lodash/debounce";
import { useCallback, useEffect, useRef, useState } from "react";
import { useQueryState } from "react-router-use-location-state";

const URL_SYNC_DELAY_MS = 300;

/**
 * Query-param state that keeps up with typing.
 *
 * Writing the URL on every keystroke re-renders the page and hands the code
 * editors — which are controlled by this value — a `value` one render behind
 * whatever has been typed since. Monaco then rewrites its buffer to match,
 * swallowing the characters in between. Local state answers reads immediately,
 * so a search fired right after typing still sees the full text, while the
 * URL, whose only job is to make the search shareable, catches up once typing
 * pauses.
 */
export const useDebouncedQueryState = (
  name: string,
  defaultValue = "",
  delay: number = URL_SYNC_DELAY_MS,
): [string, (value: string) => void] => {
  const [urlValue, setUrlValue] = useQueryState(name, defaultValue);
  const [value, setValue] = useState(urlValue);

  // The setter builds the next URL from the location of the render that
  // created it, so a deferred write has to use the newest one or it would
  // resurrect query params that changed while we waited.
  const setUrlValueRef = useRef(setUrlValue);
  setUrlValueRef.current = setUrlValue;

  // What we have queued for the URL, so a URL that is still catching up is not
  // mistaken for someone navigating back to an earlier search.
  const queuedValue = useRef<string | null>(null);

  const syncToUrl = useRef(
    _debounce((next: string) => setUrlValueRef.current(next), delay),
  ).current;

  useEffect(() => () => syncToUrl.cancel(), [syncToUrl]);

  useEffect(() => {
    if (queuedValue.current === null) {
      setValue(urlValue);
    } else if (queuedValue.current === urlValue) {
      queuedValue.current = null;
    }
  }, [urlValue]);

  const handleChange = useCallback(
    (next: string) => {
      setValue(next);
      queuedValue.current = next;
      syncToUrl(next);
    },
    [syncToUrl],
  );

  return [value, handleChange];
};

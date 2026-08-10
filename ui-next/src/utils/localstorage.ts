import { useState } from "react";
import { logger } from "./logger";

const optionalArg = {
  parse: JSON.parse,
  code: JSON.stringify,
};

// If key is null/undefined, hook behaves exactly like useState
export function useLocalStorage(
  key: string,
  initialValue: unknown,
  c = optionalArg,
) {
  const initialString = JSON.stringify(initialValue);

  const [storedValue, setStoredValue] = useState(() => {
    try {
      if (key) {
        const item = window.localStorage.getItem(key);
        return item ? c.parse(item) : initialValue;
      } else {
        return initialValue;
      }
    } catch {
      logger.error("Cant read value from local storage");
      return initialValue;
    }
  });

  const setValue = (value: unknown) => {
    // Allow value to be a function so we have same API as useState
    const valueToStore = value instanceof Function ? value(storedValue) : value;

    // Save state
    setStoredValue(valueToStore);

    if (key) {
      const stringToStore = c.code(valueToStore);
      if (stringToStore === initialString) {
        window.localStorage.removeItem(key);
      } else {
        window.localStorage.setItem(key, stringToStore);
      }
    }
  };

  return [storedValue, setValue];
}

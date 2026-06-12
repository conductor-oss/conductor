import { useState } from "react";

// If key is null/undefined, hook behaves exactly like useState
export const useLocalStorage = (key: string, initialValue: any) => {
  const initialString = JSON.stringify(initialValue);

  const [storedValue, setStoredValue] = useState(() => {
    if (key) {
      const item = window.localStorage.getItem(key);
      return item ? JSON.parse(item) : initialValue;
    } else {
      return initialValue;
    }
  });

  const setValue = (value: any) => {
    // Allow value to be a function so we have same API as useState
    const valueToStore = value instanceof Function ? value(storedValue) : value;

    // Save state
    setStoredValue(valueToStore);

    if (key) {
      const stringToStore = JSON.stringify(valueToStore);
      if (stringToStore === initialString) {
        window.localStorage.removeItem(key);
      } else {
        window.localStorage.setItem(key, stringToStore);
      }
    }
  };

  return [storedValue, setValue] as const;
};

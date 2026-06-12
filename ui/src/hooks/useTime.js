import { useEffect, useState } from "react";

export const useTime = (enabled = true, refreshCycle = 1000) => {
  const [now, setNow] = useState(new Date().getTime());

  useEffect(() => {
    const intervalId =
      enabled && setInterval(() => setNow(new Date().getTime()), refreshCycle);

    return () => clearInterval(intervalId);
  }, [refreshCycle, setNow, enabled]);

  return now;
};

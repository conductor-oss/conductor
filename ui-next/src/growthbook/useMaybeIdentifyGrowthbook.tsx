import { useEffect } from "react";
import { logger } from "utils/logger";
import { growthbook } from "./growthbookInstance";

function getGtagPseudoId() {
  const match = document.cookie.match(/_ga=GA1\.\d\.(\d+)/);
  return match ? match[1] : undefined;
}

export const useMaybeIdentifyGrowthbook = () => {
  useEffect(() => {
    if (growthbook) {
      logger.info("Initializing Growthbook");
      growthbook?.setAttributes({
        id: getGtagPseudoId(), // we use the pseudoID because we want the same experiments for the session
      });
    }
  }, []);
};

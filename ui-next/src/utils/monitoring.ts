import { isLogRocketEnabled, reportErrorToLogRocket } from "./logrocket";

export const useErrorMonitoring = () => {
  return {
    notifyError: (error: Error | string, metadata?: { [key: string]: any }) => {
      if (isLogRocketEnabled()) {
        reportErrorToLogRocket(error, {
          tags: {
            type: "metadata",
          },
          extra: {
            ...metadata,
          },
        });
      } else {
        console.error("=== ERROR ===");
        console.error(error);
        console.error("=============");
      }
    },
  };
};

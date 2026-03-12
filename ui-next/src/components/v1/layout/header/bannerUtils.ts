import { useMemo } from "react";
import { differenceInDays } from "utils/date";

export const currentDate = new Date().setHours(0, 0, 0, 0);

export const useAnnouncementBanner = (
  isTrialExpired: boolean,
  trialExpiryDate: number | Date,
  isAnnouncementBannerDismissed: boolean,
) => {
  const daysToGo = differenceInDays(trialExpiryDate!, currentDate);
  const showBanner = useMemo(() => {
    if (isAnnouncementBannerDismissed) {
      return false;
    } else {
      return true;
    }
  }, [isAnnouncementBannerDismissed]);

  return {
    showBanner:
      (trialExpiryDate && daysToGo >= 0 && showBanner) || isTrialExpired,
    daysToGo,
  };
};

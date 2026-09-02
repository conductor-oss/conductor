import { BoxProps } from "@mui/material/Box";
export interface AnnouncementBannerProps extends BoxProps {
    bannerOpen: boolean;
    setBannerOpen: (val: boolean) => void;
    trialExpiryDate: number | Date;
    isTrialExpired: boolean;
    showAiStudioBanner?: boolean;
    dismissAiStudioBanner: () => void;
    /** Whether the announcement banner has been dismissed */
    isAnnouncementBannerDismissed: boolean;
    /** Callback to dismiss the announcement banner */
    onDismissAnnouncementBanner: () => void;
}
export default function AnnouncementBanner({ sx, bannerOpen, setBannerOpen, trialExpiryDate, isTrialExpired, showAiStudioBanner, dismissAiStudioBanner, isAnnouncementBannerDismissed, onDismissAnnouncementBanner, ...rest }: AnnouncementBannerProps): import("react").JSX.Element | null;

import type { GrowthBook, UserScopedGrowthBook, GrowthBookClient, TrackingCallback } from "@growthbook/growthbook";
/**
 * This plugins are copied directly from the growthbook repo https://github.com/growthbook/growthbook
 * Given they have a bug that prevents importing for those using older js modules
 */
export type AutoAttributeSettings = {
    uuidCookieName?: string;
    uuidKey?: string;
    uuid?: string;
    uuidAutoPersist?: boolean;
};
export declare function autoAttributesPlugin(settings?: AutoAttributeSettings): (gb: GrowthBook | UserScopedGrowthBook | GrowthBookClient) => void;
export type Trackers = "gtag" | "gtm" | "segment";
export declare function thirdPartyTrackingPlugin({ additionalCallback, trackers, }?: {
    additionalCallback?: TrackingCallback;
    trackers?: Trackers[];
}): (gb: GrowthBook | UserScopedGrowthBook | GrowthBookClient) => void;

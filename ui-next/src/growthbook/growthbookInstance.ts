import { GrowthBook } from "@growthbook/growthbook";
import { featureFlags, FEATURES } from "utils/flags";
import { autoAttributesPlugin, thirdPartyTrackingPlugin } from "./plugins";

export const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);

const growthbookClientKey = featureFlags.getValue(
  FEATURES.GROWTHBOOK_CLIENT_KEY,
);

export const growthbook = isPlayground
  ? new GrowthBook({
      apiHost: "https://cdn.growthbook.io",
      clientKey: growthbookClientKey,
      // enableDevMode: true,
      plugins: [
        autoAttributesPlugin(),
        thirdPartyTrackingPlugin({ trackers: ["gtm", "gtag"] }),
      ],
    })
  : null;

growthbook?.init({
  streaming: true,
});

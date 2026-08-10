// This util means to replace gtag
import { useEffect } from "react";
import { featureFlags, FEATURES } from "utils/flags";

declare global {
  interface Window {
    gtag: any;
    dataLayer: any;
  }
}

interface EventParams {
  user_uuid?: string;
  workflow_name?: string;
  user_performed_action?: string;
  error_type?: string;
  event?: object;
  start_time?: number;
  end_time?: number;
  item_id?: string;
}

type SimpleUserInfo = {
  uuid?: string;
  user?: any;
  id?: string;
};

export const GTAG_LABEL = "G-6DLM7JND12";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);
const gtagAbstract = (event_name: string, event_params: EventParams) => {
  if (isPlayground && window && window.gtag) {
    window.gtag("event", event_name, {
      ...event_params,
      ...(process.env.NODE_ENV === "development" ? { debug_mode: true } : {}),
    });
  }
};

export const useConfigureGtagUserIdIfPlayground = (
  conductorUser?: SimpleUserInfo,
) => {
  useEffect(() => {
    if (isPlayground && window && window.gtag && conductorUser?.id) {
      window.gtag("config", GTAG_LABEL, {
        user_id: conductorUser.id,
      });
    }
  }, [conductorUser?.id]);
};
// flatten a given nested object
type FlattenedObject = Record<string, any>;

const flattenGtagObject = (
  obj: Record<string, any>,
  prefix = "",
): FlattenedObject => {
  let result: FlattenedObject = {};

  for (const key in obj) {
    const newKey = prefix ? `${prefix}_${key}` : key;

    if (
      typeof obj[key] === "object" &&
      obj[key] !== null &&
      !Array.isArray(obj[key])
    ) {
      const flattenedNestedObject = flattenGtagObject(obj[key], newKey);
      result = { ...result, ...flattenedNestedObject };
    } else if (
      key === "crumbs" &&
      Array.isArray(obj[key]) &&
      obj[key].length > 0
    ) {
      for (let i = 0; i < obj[key].length; i++) {
        const newItem = obj[key][i].ref;
        result[`${newKey}_${obj[key][i].refIdx}`] = newItem;
      }
    } else {
      result[newKey] = obj[key];
    }
  }

  return result;
};

export { gtagAbstract, flattenGtagObject };

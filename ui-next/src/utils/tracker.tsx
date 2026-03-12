import { Helmet } from "react-helmet";
import { featureFlags, FEATURES } from "./flags";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);
const isUserManagement = featureFlags.isEnabled(FEATURES.ACCESS_MANAGEMENT);
const logRocketKey = featureFlags.getValue(FEATURES.HEAP_APP_ID);
const isHeapEnabled = () => isPlayground && isUserManagement && logRocketKey;

export const MaybeHeapHelmet = () => {
  return isHeapEnabled() ? (
    <Helmet>
      <script type="text/javascript" nonce="tpsHAxwU5x0csoIuLNs2vg==">
        {`window.heap=window.heap||[],heap.load=function(e,t){window.heap.appid=e,window.heap.config=t=t||{};var r=document.createElement("script");r.type="text/javascript",r.async=!0,r.src="https://cdn.heapanalytics.com/js/heap-"+e+".js";var a=document.getElementsByTagName("script")[0];a.parentNode.insertBefore(r,a);for(var n=function(e){return function(){heap.push([e].concat(Array.prototype.slice.call(arguments,0)))}},p=["addEventProperties","addUserProperties","clearEventProperties","identify","resetIdentity","removeEventProperty","setEventProperties","track","unsetEventProperty"],o=0;o<p.length;o++)heap[p[o]]=n(p[o])};
  heap.load("${logRocketKey}");`}
      </script>
    </Helmet>
  ) : null;
};

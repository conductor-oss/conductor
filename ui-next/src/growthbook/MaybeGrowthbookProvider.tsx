import { GrowthBookProvider } from "@growthbook/growthbook-react";
// import { gtagAbstract } from "utils/gtag";
import { GrowthBook } from "@growthbook/growthbook";
import { ReactNode } from "react";
import { growthbook, isPlayground } from "./growthbookInstance";

export const MaybeGrowthbookProvider = ({
  children,
}: {
  children: ReactNode;
}) => {
  return isPlayground ? (
    <GrowthBookProvider
      growthbook={growthbook as GrowthBook<Record<string, any>>}
    >
      {children}
    </GrowthBookProvider>
  ) : (
    children
  );
};

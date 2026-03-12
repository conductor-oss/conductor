import { FunctionComponent } from "react";
import { FlowActorContext, FlowContextProps } from "./FlowActorContext";

export const FlowMachineContextProvider: FunctionComponent<
  FlowContextProps
> = ({ flowActor, children }) => (
  <FlowActorContext.Provider
    value={{
      flowActor,
    }}
  >
    {children}
  </FlowActorContext.Provider>
);

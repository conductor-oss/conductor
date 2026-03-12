import { useSelector } from "@xstate/react";
import { ReactNode } from "react";
import { ActorRef } from "xstate";
import {
  MetadataFieldMachineEventTypes,
  MetdataFieldMachineEvents,
} from "./state";

interface ChildrenProps {
  onChange: (value: any) => void;
  value: any;
  someKey: string;
}

export interface ActorToHandlerValueProps {
  children: (props: ChildrenProps) => ReactNode;
  actor: ActorRef<MetdataFieldMachineEvents>;
}

export const ActorToHandlerValue = ({
  actor,
  children,
}: ActorToHandlerValueProps) => {
  const send = actor.send;
  const value = useSelector(actor, (state) => state.context.value);
  const someKey = useSelector(actor, (state) => state.context.someKey);
  const handleValueChange = (value: any) => {
    send({
      type: MetadataFieldMachineEventTypes.CHANGE_VALUE,
      value,
    });
  };
  return children({ onChange: handleValueChange, value, someKey });
};

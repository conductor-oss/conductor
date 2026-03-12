import { useEffect } from "react";
import { EventObject, ActorRef, State } from "xstate";

function useXStateEventListener<TContext, TEvent extends EventObject>(
  actorRef: ActorRef<TEvent, State<TContext, TEvent>>,
  eventType: TEvent["type"],
  callback: (event: TEvent) => void,
) {
  useEffect(() => {
    const subscription = actorRef.subscribe((state) => {
      if (state.event.type === eventType) {
        callback(state.event);
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [actorRef, eventType, callback]);
}

export default useXStateEventListener;

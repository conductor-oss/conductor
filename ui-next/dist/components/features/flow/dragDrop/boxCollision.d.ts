import { CollisionDetection, CollisionDescriptor } from "@dnd-kit/core";
import { ActorRef } from "xstate";
import { PanAndZoomEvents } from "../components/graphs/PanAndZoomWrapper/state/types";
export declare function sortCollisionsDesc({ data: { value: a } }: CollisionDescriptor, { data: { value: b } }: CollisionDescriptor): number;
export declare const useNodeCollisionDetection: (panAndZoomActor: ActorRef<PanAndZoomEvents>) => CollisionDetection;

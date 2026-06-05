import {
  Active,
  CollisionDetection,
  ClientRect,
  CollisionDescriptor,
} from "@dnd-kit/core";
import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import { PanAndZoomEvents } from "../components/graphs/PanAndZoomWrapper/state/types";

export function sortCollisionsDesc(
  { data: { value: a } }: CollisionDescriptor,
  { data: { value: b } }: CollisionDescriptor,
) {
  return b - a;
}

/**
 * Returns the intersecting rectangle area between two rectangles
 */
function getIntersectionRatio(entry: ClientRect, active: Active): number {
  const {
    top: currentTop = 0,
    left: currentLeft = 0,
    width: currentWidth = 0,
    height: currentHeight = 0,
  } = active.rect.current.translated ?? {};

  const top = Math.max(currentTop, entry.top);
  const left = Math.max(currentLeft, entry.left);
  const right = Math.min(currentLeft + currentWidth, entry.left + entry.width);
  const bottom = Math.min(currentTop + currentHeight, entry.top + entry.height);
  const width = right - left;
  const height = bottom - top;

  if (left < right && top < bottom) {
    const targetArea = currentWidth * currentHeight;
    const entryArea = entry.width * entry.height;
    const intersectionArea = width * height;
    const intersectionRatio =
      intersectionArea / (targetArea + entryArea - intersectionArea);

    return Number(intersectionRatio.toFixed(4));
  }

  // Rectangles do not overlap, or overlap has an area of zero (edge/corner overlap)
  return 0;
}

/**
 * Returns the rectangle that has the greatest intersection area with a given
 * rectangle in an array of rectangles.
 */
const performantRectIntersection = (useDom = false) => {
  const activeRectIntersection: CollisionDetection = ({
    active,
    droppableContainers,
  }) => {
    let maxIntersectionRatio = 0;
    const collisions: CollisionDescriptor[] = [];
    for (const droppableContainer of droppableContainers) {
      const { id } = droppableContainer;
      const {
        rect: { current: rect },
      } = droppableContainer;

      if (rect) {
        // Workaround to account for the movement of the position.
        const actualRect = useDom
          ? droppableContainer.node.current?.getBoundingClientRect() || rect
          : rect;
        const intersectionRatio = getIntersectionRatio(actualRect, active);

        if (intersectionRatio > maxIntersectionRatio) {
          maxIntersectionRatio = intersectionRatio;
          collisions.push({
            id,
            data: { droppableContainer, value: intersectionRatio },
          });
        }
      }
    }

    return collisions.sort(sortCollisionsDesc);
  };
  return activeRectIntersection;
};

export const useNodeCollisionDetection = (
  panAndZoomActor: ActorRef<PanAndZoomEvents>,
) => {
  /**
   * This is a workaround to account for the movement of the position.
   * we don't want to hit the dom if the user has not dragged passed his position. Else we hit the dom.
   */
  const useDom = useSelector(
    panAndZoomActor!,
    (state) => state.context.draggingUpdatedPosition,
  );
  return performantRectIntersection(useDom);
};

import {
  useCallback,
  useMemo,
  useState,
  KeyboardEvent,
  MouseEvent,
} from "react";
import _nth from "lodash/fp/nth";
import _first from "lodash/fp/first";
import _last from "lodash/fp/last";

type useArrowNavigationProps<T> = {
  onSelect: (item: T) => void;
  options: T[];
  optionsIdGen: (v: T) => string;
  scrollToCenter: boolean;
  hoveredItem: string;
  setHoveredItem: (item: string) => void;
};

export type OptionPropsForItemT = {
  onMouseMove: (e: any) => void;
  onMouseLeave: (e: any) => void;
  id: string;
};

function useArrowNavigation<T>({
  onSelect,
  options,
  optionsIdGen,
  scrollToCenter,
  hoveredItem,
  setHoveredItem,
}: useArrowNavigationProps<T>) {
  const [lastCursorPos, setLastCursorPos] = useState({ x: 0, y: 0 });

  const [firstOptionItemHash, lastOptionItemHash] = useMemo(() => {
    const head = _first(options);
    const tail = _last(options);

    return [
      head ? optionsIdGen(head) : undefined,
      tail ? optionsIdGen(tail) : undefined,
    ];
  }, [options, optionsIdGen]);

  const [hoveredOptionValue, hoveredOptionValueIndex] = useMemo(() => {
    const idx = options.findIndex(
      (item: T) => hoveredItem === optionsIdGen(item),
    );
    if (idx === -1) {
      return [undefined, -1];
    }
    return [_nth(idx, options), idx];
  }, [hoveredItem, options, optionsIdGen]);

  const moveDown = useCallback(() => {
    if (hoveredItem !== "") {
      if (options && options.length > 0) {
        //get the index of hoveredItem from options and then add +1
        const nextIndex = hoveredOptionValueIndex + 1;
        const maybeNextItem = _nth(
          nextIndex < options.length ? nextIndex : 0,
          options,
        );

        if (maybeNextItem) {
          const nextElementHash = optionsIdGen(maybeNextItem);
          setHoveredItem(nextElementHash);
          if (typeof window !== "undefined") {
            window.document.getElementById(nextElementHash)?.scrollIntoView({
              behavior: "smooth",
              block: scrollToCenter ? "center" : "nearest",
              inline: scrollToCenter ? "center" : "start",
            });
          }
        }
      }
    } else if (firstOptionItemHash) {
      setHoveredItem(firstOptionItemHash);
    }
  }, [
    firstOptionItemHash,
    hoveredItem,
    hoveredOptionValueIndex,
    options,
    optionsIdGen,
    scrollToCenter,
    setHoveredItem,
  ]);

  const moveUp = useCallback(() => {
    if (hoveredItem !== "") {
      if (options && options.length > 0) {
        //get the index of hoveredItem from options and then add -1
        const maybePreviousItem = _nth(hoveredOptionValueIndex - 1, options);

        if (maybePreviousItem) {
          const previousElementHash = optionsIdGen(maybePreviousItem);
          setHoveredItem(previousElementHash);
          if (typeof window !== "undefined") {
            window.document
              .getElementById(previousElementHash)
              ?.scrollIntoView({
                behavior: "smooth",
                block: scrollToCenter ? "center" : "nearest",
                inline: scrollToCenter ? "center" : "start",
              });
          }
        }
      }
    } else if (lastOptionItemHash) {
      setHoveredItem(lastOptionItemHash);
    }
  }, [
    lastOptionItemHash,
    hoveredItem,
    hoveredOptionValueIndex,
    options,
    optionsIdGen,
    scrollToCenter,
    setHoveredItem,
  ]);

  const handleKeyDown = useCallback(
    (event: KeyboardEvent<HTMLElement>) => {
      if (event.key === "Enter") {
        if (options && hoveredItem) {
          if (hoveredOptionValue) {
            onSelect(hoveredOptionValue);
          }
        }
      }
      if (event.key === "ArrowDown") {
        event.preventDefault();
        moveDown();
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        moveUp();
      }
    },
    [options, hoveredItem, hoveredOptionValue, moveDown, moveUp, onSelect],
  );

  const handleMouseLeave = useCallback((event: MouseEvent<HTMLElement>) => {
    setLastCursorPos({ x: event.screenX, y: event.screenY });
  }, []);
  const handleMouseOver = (e: MouseEvent<HTMLElement>, index: string) => {
    const currentCursorPos = {
      x: e.screenX,
      y: e.screenY,
    };

    if (
      currentCursorPos.x === lastCursorPos.x &&
      currentCursorPos.y === lastCursorPos.y
    ) {
      return;
    }
    setLastCursorPos({ x: e.screenX, y: e.screenY });

    setHoveredItem(index);
  };

  const optionPropsForItem = (item: T): OptionPropsForItemT => {
    return {
      onMouseMove: (e: any) => {
        handleMouseOver(e, optionsIdGen(item));
      },
      onMouseLeave: (e: any) => {
        handleMouseLeave(e);
      },
      id: optionsIdGen(item),
    };
  };

  const inputProps = {
    onKeyDown: handleKeyDown,
  };

  return {
    inputProps,
    optionPropsForItem,
    hoveredItem,
    moveUp,
    moveDown,
  } as const;
}

export default useArrowNavigation;
export type { useArrowNavigationProps };

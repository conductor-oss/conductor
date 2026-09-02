import { ActorRef } from "xstate";
import { PanAndZoomEvents, PositionProps } from "./types";
export declare const usePanAndZoomActor: (panAndZoomActor: ActorRef<PanAndZoomEvents>) => readonly [{
    readonly zoom: any;
    readonly canvasSize: any;
    readonly layout: any;
    readonly position: any;
    readonly panEnabled: any;
    readonly viewportSize: any;
    readonly isSearchFieldVisible: any;
    readonly isPanAndZoomIdle: any;
    readonly notifiedEventType: any;
}, {
    readonly handleResetZoomPosition: (viewportOffsetWidth: number, viewportOffsetHeight: number) => void;
    readonly handleSetZoom: (zoom: number) => void;
    readonly handleSetPosition: (position: PositionProps) => void;
    readonly handleCenterOnSelectedTask: (viewportOffsetWidth: number, viewportOffsetHeight: number) => void;
    readonly handleSetInitialViewportOffset: (viewportOffsetWidth: number, viewportOffsetHeight: number) => void;
    readonly handleSetFullScreen: (fullScreen: boolean, viewportOffsetWidth: number) => void;
    readonly handleSetFitScreen: (viewportOffsetWidth: number, viewportOffsetHeight: number) => void;
    readonly handleZoom: (isZoomOut: boolean) => void;
    readonly handleTogglePan: () => void;
    readonly handleDrag: (position: PositionProps, clientMousePosition: PositionProps) => void;
    readonly handleSetZoomAndPosition: (position: PositionProps, zoom: number) => void;
    readonly handleToggleSearchField: () => void;
    readonly handleSelectSearchResult: (viewportOffsetWidth: number, viewportOffsetHeight: number) => void;
    readonly handleSetEventType: (eventType: string) => void;
}];

import { applyZoomToCursor } from "./helpers";

const zoomCases = [
  {
    description: "Zoom out with same cursor position",
    currentPosition: { x: 100, y: 100 },
    cursorPosition: { x: 100, y: 100 },
    oldZoom: 0.5,
    newZoom: 0.6,
    expected: {
      position: {
        x: 100,
        y: 100,
      },
      zoom: 0.6,
    },
  },
  {
    description: "Zoom out with different cursor position",
    currentPosition: { x: 100, y: 100 },
    cursorPosition: { x: 200, y: 200 },
    oldZoom: 0.5,
    newZoom: 0.6,
    expected: {
      position: {
        x: 80,
        y: 80,
      },
      zoom: 0.6,
    },
  },
  {
    description: "Zoom in with same cursor position",
    currentPosition: { x: 100, y: 100 },
    cursorPosition: { x: 100, y: 100 },
    oldZoom: 0.5,
    newZoom: 0.4,
    expected: {
      position: {
        x: 100,
        y: 100,
      },
      zoom: 0.4,
    },
  },
  {
    description: "Zoom in with different cursor position",
    currentPosition: { x: 100, y: 100 },
    cursorPosition: { x: 200, y: 200 },
    oldZoom: 0.5,
    newZoom: 0.4,
    expected: {
      position: {
        x: 120,
        y: 120,
      },
      zoom: 0.4,
    },
  },
];

describe("Testing applyZoomToCursor function", () => {
  test.each(zoomCases)(
    "Testing $description: given $oldZoom and $newZoom as arguments, returns $expected",
    ({ oldZoom, newZoom, currentPosition, cursorPosition, expected }) => {
      const result = applyZoomToCursor(
        currentPosition,
        cursorPosition,
        oldZoom,
        newZoom,
      );

      expect(result).toMatchObject(expected);
    },
  );
});

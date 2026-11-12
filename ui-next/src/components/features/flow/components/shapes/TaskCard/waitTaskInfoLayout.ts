import {
  fontFamily,
  fontSizes,
  fontWeights,
  lineHeights,
} from "theme/tokens/variables";
import { WaitTaskDef } from "types";

// WaitTaskInfo renders as MUI body1 text at weight 600.
const FONT_SIZE = parseFloat(fontSizes.fontSize2);
const FONT = `${fontWeights.fontWeight3} ${FONT_SIZE}px ${fontFamily.fontFamilySans}`;

/** Height of one line of WaitTaskInfo text, in diagram pixels. */
export const WAIT_INFO_LINE_HEIGHT = FONT_SIZE * lineHeights.lineHeight1;

// Width the text gets in a default-width node: 350px node, minus the card's
// padding, WaitTaskInfo's 12px side padding, and the 16px clock icon + 6px gap
// (measured: 258px). A few pixels are held back because layout can run before
// the web font has loaded, while the browser still measures a fallback font.
const TEXT_WIDTH = 258 - 6;

// Used where canvas text measurement isn't available (e.g. jsdom in tests).
// Deliberately wide, so the estimate errs toward an extra line.
const FALLBACK_CHAR_WIDTH = FONT_SIZE * 0.55;

export const waitTaskInfoText = (task?: WaitTaskDef): string | null => {
  const duration = task?.inputParameters?.duration;
  const until = task?.inputParameters?.until;
  if (until) return `Until: ${until}`;
  if (duration) return `Duration: ${duration}`;
  return null;
};

let context: OffscreenCanvasRenderingContext2D | null | undefined;

const measurer = (): ((text: string) => number) => {
  if (context === undefined) {
    context =
      typeof OffscreenCanvas === "undefined"
        ? null
        : new OffscreenCanvas(0, 0).getContext("2d");
    if (context) context.font = FONT;
  }
  const ctx = context;
  return ctx
    ? (text) => ctx.measureText(text).width
    : (text) => text.length * FALLBACK_CHAR_WIDTH;
};

/**
 * How many lines `text` wraps to in WaitTaskInfo. Mirrors the browser with
 * `overflow-wrap: anywhere`: break at spaces where possible, and break inside
 * a word only when it can't fit on a line by itself (e.g. a ${...} expression).
 */
export const waitTaskInfoLineCount = (
  text: string,
  maxWidth = TEXT_WIDTH,
): number => {
  const measure = measurer();
  const spaceWidth = measure(" ");
  let lines = 1;
  let lineWidth = 0;

  for (const word of text.split(" ")) {
    const wordWidth = measure(word);
    if (lineWidth > 0 && lineWidth + spaceWidth + wordWidth <= maxWidth) {
      lineWidth += spaceWidth + wordWidth;
      continue;
    }
    if (lineWidth > 0) {
      lines++;
      lineWidth = 0;
    }
    if (wordWidth <= maxWidth) {
      lineWidth = wordWidth;
      continue;
    }
    // A word longer than a whole line breaks between any two characters.
    for (const char of word) {
      const charWidth = measure(char);
      if (lineWidth > 0 && lineWidth + charWidth > maxWidth) {
        lines++;
        lineWidth = 0;
      }
      lineWidth += charWidth;
    }
  }
  return lines;
};

/** Extra node height WaitTaskInfo needs beyond its first line. */
export const waitTaskInfoExtraHeight = (task?: WaitTaskDef): number => {
  const text = waitTaskInfoText(task);
  return text ? (waitTaskInfoLineCount(text) - 1) * WAIT_INFO_LINE_HEIGHT : 0;
};

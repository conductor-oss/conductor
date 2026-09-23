/**
 * Shared geometry for the Event and Code tab bodies. Both render a fixed-width
 * column centred in the tab container; keeping the numbers here stops the two
 * tabs drifting apart, which showed up as the content jumping sideways when
 * switching between them.
 */
export const TAB_COLUMN_WIDTH = 820;

/**
 * Desktop cap. The column is `flex: 1`, so this is only ever an upper bound —
 * it shrinks to whatever the viewport leaves after the nav rail, and never
 * forces a horizontal scroll.
 */
export const TAB_COLUMN_WIDTH_WIDE = 1120;

// NB: this theme's spacing unit is 4px, not MUI's default 8px.
export const tabSurfaceStyle = {
  display: "flex",
  justifyContent: "center",
  p: 4,
};

export const tabColumnStyle = {
  flex: 1,
  minWidth: 0,
  maxWidth: { xs: TAB_COLUMN_WIDTH, md: TAB_COLUMN_WIDTH_WIDE },
};

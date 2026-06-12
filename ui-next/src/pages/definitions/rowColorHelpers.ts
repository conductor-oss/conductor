type RowWithActive = { active?: boolean };

export const activeFilterGroups = [
  { title: "Active", value: "yes" },
  { title: "Inactive", value: "no" },
  { title: "Both", value: "all" },
];
// TODO this should be in the colors file. FIXME ask Leah!
export const pausedrowColor = "#949494";
export const pausedLinkColor = "#619bd5";
export const activeLinkColor = "#1976d2";

export const getLinkColor = (rec: RowWithActive) =>
  rec.active ? activeLinkColor : pausedLinkColor;

export const conditionalRowStyles = [
  {
    when: (row: RowWithActive) => row.active === false,
    cl: "pausedrow",
    style: {
      color: `${pausedrowColor}`,
    },
  },
];

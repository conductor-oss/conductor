import { columns, getDefaultShowColumns } from "../Schedules";

// "actions" is appended to the columns inside the component.
const columnIds = [...columns.map((column) => column.id), "actions"];

describe("Schedules default columns", () => {
  it.each([true, false])(
    "only names columns that exist (tagsEnabled=%s)",
    (tagsEnabled) => {
      for (const id of getDefaultShowColumns(tagsEnabled)) {
        expect(columnIds).toContain(id);
      }
    },
  );

  it("shows the cron expression column by default", () => {
    expect(getDefaultShowColumns(false)).toContain("cronExpression");
  });
});

import { render, screen } from "@testing-library/react";
import { IScheduleDto } from "types/Schedulers";
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

describe("cron expression column", () => {
  // `columns` is heterogeneous, so each renderer's own signature is narrowed away on the union.
  const renderer = columns.find((each) => each.id === "cronExpression")!
    .renderer as (cron: string, row: IScheduleDto) => React.ReactNode;
  const renderCell = (row: Partial<IScheduleDto>) =>
    render(<>{renderer(row.cronExpression as string, row as IScheduleDto)}</>);

  it("describes a single expression", () => {
    renderCell({ cronExpression: "0 0 9 * * ?", zoneId: "UTC" });

    expect(screen.getByText("At 09:00 AM")).toBeInTheDocument();
  });

  it("describes every expression of a multi-cron schedule", () => {
    // A schedule running on several crons would otherwise show only the first.
    renderCell({
      cronExpression: "0 0 9 * * ?",
      zoneId: "Asia/Kolkata",
      cronSchedules: [
        { cronExpression: "0 0 9 * * ?", zoneId: "Asia/Kolkata" },
        { cronExpression: "0 0 18 * * ?", zoneId: "America/New_York" },
      ],
    });

    expect(screen.getByText("At 09:00 AM; At 06:00 PM")).toBeInTheDocument();
  });

  it("renders nothing when the schedule has no cron at all", () => {
    const { container } = renderCell({ cronSchedules: [] });

    expect(container).toBeEmptyDOMElement();
  });
});

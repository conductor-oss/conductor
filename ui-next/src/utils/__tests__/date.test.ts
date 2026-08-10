import { printableUpdatedTime } from "utils/date";

describe("printableUpdatedTime", () => {
  afterEach(() => {
    vi.useRealTimers(); // restore real timers after each test
  });

  it('should return "0" if updatedTimeInMillis is null', () => {
    const result = printableUpdatedTime(null as unknown as number);
    expect(result).toBe("0 minutes ago");
  });

  it('should return "0" if updatedTimeInMillis is 0', () => {
    const result = printableUpdatedTime(0);
    expect(result).toBe("0 minutes ago");
  });

  it("should handle time difference in minutes", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 9, 1, 12, 0, 0)); // Set a fixed date
    const result = printableUpdatedTime(new Date(2024, 9, 1, 11, 58).getTime()); // 2 minutes ago
    expect(result).toBe("2 minutes ago");
  });

  it("should handle time difference in days", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 9, 10, 12, 0, 0)); // Set a fixed date
    const result = printableUpdatedTime(
      new Date(2024, 9, 5, 12, 0, 0).getTime(),
    ); // 5 days ago
    expect(result).toBe("5 days ago");
  });

  it("should handle time difference in months", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 9, 1, 12, 0, 0)); // Set a fixed date
    const result = printableUpdatedTime(
      new Date(2024, 6, 1, 12, 0, 0).getTime(),
    ); // 3 months ago
    expect(result).toBe("3 months ago");
  });

  it("should handle time difference in years", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 9, 1, 12, 0, 0)); // Set a fixed date
    const result = printableUpdatedTime(
      new Date(2022, 9, 1, 12, 0, 0).getTime(),
    ); // 2 years ago
    expect(result).toBe("about 2 years ago");
  });
});

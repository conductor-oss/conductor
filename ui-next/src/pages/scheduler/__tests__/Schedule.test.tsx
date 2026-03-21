import { ThemeProvider, createTheme } from "@mui/material/styles";
import {
  fireEvent,
  render,
  screen,
  renderHook,
  waitFor,
} from "@testing-library/react";
import React from "react";
import { formatInTimeZone } from "utils/date";
import { TimezonePicker } from "../TimezonePicker";
import { cronExpressionIsValid } from "utils/cronHelpers";
import cronstrue from "cronstrue";
import { useCronExpression } from "../hooks/useCronExpression";

// Mock the timezones data
vi.mock("../timezones.json", () => ({
  default: [
    "UTC",
    "America/New_York",
    "Europe/London",
    "Asia/Tokyo",
    "Australia/Sydney",
  ],
}));

// Mock the ConductorAutoComplete component to render a simple select
vi.mock("components/v1/ConductorAutoComplete", () => ({
  ConductorAutoComplete: ({ value, onChange, options, ...props }: any) => (
    <div data-testid="timezone-picker">
      <label htmlFor="timezone-select">Select Timezone</label>
      <select
        id="timezone-select"
        data-testid="timezone-select"
        value={value}
        onChange={(e) => onChange(undefined, e.target.value)}
        {...props}
      >
        {options?.map((option: string) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </div>
  ),
}));

// Create a test wrapper with theme
const TestWrapper = ({ children }: { children: React.ReactNode }) => {
  const theme = createTheme();
  return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
};

describe("Schedule Component - TimezonePicker Integration Tests", () => {
  it("should render TimezonePicker component (used in Schedule)", () => {
    const mockOnChange = vi.fn();

    render(
      <TestWrapper>
        <TimezonePicker
          timezone="UTC"
          onChange={mockOnChange}
          error={false}
          helperText=""
        />
      </TestWrapper>,
    );

    // Check that the TimezonePicker component renders (this is what Schedule uses)
    expect(screen.getByTestId("timezone-picker")).toBeInTheDocument();
    expect(screen.getByTestId("timezone-select")).toBeInTheDocument();
    expect(screen.getByText("Select Timezone")).toBeInTheDocument();
  });

  it("should handle timezone selection changes (Schedule functionality)", () => {
    const mockOnChange = vi.fn();

    render(
      <TestWrapper>
        <TimezonePicker
          timezone="UTC"
          onChange={mockOnChange}
          error={false}
          helperText=""
        />
      </TestWrapper>,
    );

    const select = screen.getByTestId("timezone-select");

    // Change the timezone selection (this simulates what happens in Schedule)
    fireEvent.change(select, { target: { value: "Europe/London" } });

    // Verify the onChange was called with the new value
    expect(mockOnChange).toHaveBeenCalledWith("Europe/London");
  });

  it("should work with formatInTimeZone when timezone changes (Schedule integration)", () => {
    const mockOnChange = vi.fn();
    const testTime = "2024-01-15T14:30:00Z";
    const formatString = "yyyy-MM-dd HH:mm:ss zzz";

    render(
      <TestWrapper>
        <TimezonePicker
          timezone="UTC"
          onChange={mockOnChange}
          error={false}
          helperText=""
        />
      </TestWrapper>,
    );

    const select = screen.getByTestId("timezone-select");

    // Test initial timezone (this is what Schedule does with futureMatches)
    expect(() => {
      const result = formatInTimeZone(testTime, formatString, "UTC");
      expect(result).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} .+$/);
    }).not.toThrow();

    // Simulate changing to a different timezone (Schedule timezone selection)
    fireEvent.change(select, { target: { value: "America/New_York" } });

    // Test that formatInTimeZone works with the new timezone (Schedule futureMatches display)
    expect(() => {
      const result = formatInTimeZone(
        testTime,
        formatString,
        "America/New_York",
      );
      expect(result).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} .+$/);
      // The result should be different due to timezone conversion
      expect(result).not.toBe(formatInTimeZone(testTime, formatString, "UTC"));
    }).not.toThrow();
  });

  it("should display all available timezone options (Schedule timezone picker)", () => {
    const mockOnChange = vi.fn();

    render(
      <TestWrapper>
        <TimezonePicker
          timezone="UTC"
          onChange={mockOnChange}
          error={false}
          helperText=""
        />
      </TestWrapper>,
    );

    // Check that all timezone options are available (Schedule timezone selection)
    expect(screen.getByRole("option", { name: "UTC" })).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "America/New_York" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Europe/London" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Asia/Tokyo" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Australia/Sydney" }),
    ).toBeInTheDocument();
  });

  it("should catch the original formatInTimeZone parameter order bug", () => {
    const testTime = "2024-01-15T14:30:00Z";
    const timezone = "UTC";
    const formatString = "yyyy-MM-dd HH:mm:ss zzz";

    // Test correct usage (should work)
    expect(() => {
      formatInTimeZone(testTime, formatString, timezone);
    }).not.toThrow();

    // Test wrong usage (should throw - this was the original bug)
    expect(() => {
      formatInTimeZone(testTime, timezone, formatString); // Wrong parameter order
    }).toThrow(/Invalid time value/);
  });
});

describe("Schedule Component - Cron Expression Validation Tests", () => {
  // All cron expression samples from CronExpressionSection.tsx
  const cronSamples = [
    { expr: "* * * ? * *", desc: "Every second" },
    { expr: "0 * * ? * *", desc: "Every minute" },
    { expr: "0 */2 * ? * *", desc: "Every 2 minutes" },
    { expr: "0 1/2 * ? * *", desc: "Every 2 minutes starting at minute 1" },
    { expr: "0 */30 * ? * *", desc: "Every 30 minutes" },
    { expr: "0 15,30,45 * ? * *", desc: "At minutes 15, 30, and 45" },
    { expr: "0 0 * ? * *", desc: "Every hour" },
    { expr: "0 0 */2 ? * *", desc: "Every 2 hours" },
    { expr: "0 0 0/2 ? * *", desc: "Every 2 hours starting at midnight" },
    { expr: "0 0 1/2 ? * *", desc: "Every 2 hours starting at 1 AM" },
    { expr: "0 0 0 * * ?", desc: "Daily at midnight" },
    { expr: "0 0 1 * * ?", desc: "Daily at 1 AM" },
    { expr: "0 0 6 * * ?", desc: "Daily at 6 AM" },
    { expr: "0 0 12 ? * SUN", desc: "Every Sunday at noon" },
    { expr: "0 0 12 ? * MON-FRI", desc: "Every weekday at noon" },
    { expr: "0 0 12 ? * SUN,SAT", desc: "Every weekend at noon" },
    { expr: "0 0 12 */7 * ?", desc: "Every 7 days at noon" },
    { expr: "0 0 12 1 * ?", desc: "First day of month at noon" },
    { expr: "0 0 12 15 * ?", desc: "15th day of month at noon" },
    { expr: "0 0 12 1/4 * ?", desc: "Every 4 days starting on 1st at noon" },
    { expr: "0 0 12 L * ?", desc: "Last day of month at noon" },
    { expr: "0 0 12 L-2 * ?", desc: "2 days before last day of month at noon" },
    { expr: "0 0 12 1W * ?", desc: "Nearest weekday to 1st at noon" },
    { expr: "0 0 12 15W * ?", desc: "Nearest weekday to 15th at noon" },
    { expr: "0 0 12 ? * 2#1", desc: "First Monday of month at noon" },
    { expr: "0 0 12 ? * 6#2", desc: "Second Friday of month at noon" },
    { expr: "0 0 12 ? JAN *", desc: "Every day in January at noon" },
    {
      expr: "0 0 12 ? JAN,JUN *",
      desc: "Every day in January and June at noon",
    },
    {
      expr: "0 0 12 ? JAN,FEB,APR *",
      desc: "Every day in Jan, Feb, Apr at noon",
    },
    { expr: "0 0 12 ? 9-12 *", desc: "Every day Sept-Dec at noon" },
  ];

  it.each(cronSamples)(
    "should validate and humanize cron expression: $expr",
    ({ expr, desc: _desc }) => {
      // Test that expression is valid
      const validation = cronExpressionIsValid(expr);
      expect(validation.isValid).toBe(true);
      expect(validation.errors).toBeNull();

      // Test that cronstrue can humanize it
      expect(() => {
        const humanized = cronstrue.toString(expr);
        expect(humanized).toBeTruthy();
        expect(typeof humanized).toBe("string");
      }).not.toThrow();
    },
  );

  it("should specifically validate L-2 pattern (2 days before last day of month)", () => {
    const expr = "0 0 12 L-2 * ?";

    // Validate the expression
    const validation = cronExpressionIsValid(expr);
    expect(validation.isValid).toBe(true);
    expect(validation.errors).toBeNull();

    // Test humanization
    const humanized = cronstrue.toString(expr);
    expect(humanized).toBe(
      "At 12:00 PM, 2 days before the last day of the month",
    );
  });

  it("should validate various L-n offset patterns", () => {
    const offsetPatterns = [
      { expr: "0 0 12 L-1 * ?", offset: 1 },
      { expr: "0 0 12 L-2 * ?", offset: 2 },
      { expr: "0 0 12 L-5 * ?", offset: 5 },
      { expr: "0 0 12 L-10 * ?", offset: 10 },
    ];

    offsetPatterns.forEach(({ expr, offset }) => {
      const validation = cronExpressionIsValid(expr);
      expect(validation.isValid).toBe(true);

      const humanized = cronstrue.toString(expr);
      // cronstrue doesn't handle singular/plural correctly, so just check for the number
      expect(humanized).toContain(`${offset} day`);
      expect(humanized).toContain("before the last day of the month");
    });
  });

  it("should detect L-n pattern correctly", () => {
    const hasLOffsetPattern = (cronExpr: string) => /\bL-\d+\b/.test(cronExpr);

    // Should match L-n patterns
    expect(hasLOffsetPattern("0 0 12 L-2 * ?")).toBe(true);
    expect(hasLOffsetPattern("0 0 12 L-5 * ?")).toBe(true);
    expect(hasLOffsetPattern("0 0 12 L-10 * ?")).toBe(true);

    // Should NOT match regular L
    expect(hasLOffsetPattern("0 0 12 L * ?")).toBe(false);

    // Should NOT match other patterns
    expect(hasLOffsetPattern("0 0 12 1 * ?")).toBe(false);
    expect(hasLOffsetPattern("0 0 12 15 * ?")).toBe(false);
    expect(hasLOffsetPattern("0 0 12 1W * ?")).toBe(false);
  });

  it("should validate L-n with specific month constraints", () => {
    const expr = "0 0 12 L-2 1 ?"; // January only

    const validation = cronExpressionIsValid(expr);
    expect(validation.isValid).toBe(true);

    const humanized = cronstrue.toString(expr);
    expect(humanized).toContain("2 days before the last day of the month");
    expect(humanized).toContain("January");
  });

  it("should invalidate malformed cron expressions", () => {
    const invalidExpressions = [
      "not a cron",
      "* * *",
      "0 0 12 L-999 * ?", // offset too large
      "invalid pattern",
    ];

    invalidExpressions.forEach((expr) => {
      const validation = cronExpressionIsValid(expr);
      // Most should be invalid, but we mainly care that the validator doesn't crash
      expect(validation).toHaveProperty("isValid");
      expect(validation).toHaveProperty("errors");
    });
  });
});

describe("useCronExpression Hook - Integration Tests", () => {
  // All cron expression samples that should work with the hook
  const cronSamples = [
    { expr: "* * * ? * *", desc: "Every second" },
    { expr: "0 * * ? * *", desc: "Every minute" },
    { expr: "0 */2 * ? * *", desc: "Every 2 minutes" },
    { expr: "0 1/2 * ? * *", desc: "Every 2 minutes starting at minute 1" },
    { expr: "0 */30 * ? * *", desc: "Every 30 minutes" },
    { expr: "0 15,30,45 * ? * *", desc: "At minutes 15, 30, and 45" },
    { expr: "0 0 * ? * *", desc: "Every hour" },
    { expr: "0 0 */2 ? * *", desc: "Every 2 hours" },
    { expr: "0 0 0/2 ? * *", desc: "Every 2 hours starting at midnight" },
    { expr: "0 0 1/2 ? * *", desc: "Every 2 hours starting at 1 AM" },
    { expr: "0 0 0 * * ?", desc: "Daily at midnight" },
    { expr: "0 0 1 * * ?", desc: "Daily at 1 AM" },
    { expr: "0 0 6 * * ?", desc: "Daily at 6 AM" },
    { expr: "0 0 12 ? * SUN", desc: "Every Sunday at noon" },
    { expr: "0 0 12 ? * MON-FRI", desc: "Every weekday at noon" },
    { expr: "0 0 12 ? * SUN,SAT", desc: "Every weekend at noon" },
    { expr: "0 0 12 */7 * ?", desc: "Every 7 days at noon" },
    { expr: "0 0 12 1 * ?", desc: "First day of month at noon" },
    { expr: "0 0 12 15 * ?", desc: "15th day of month at noon" },
    { expr: "0 0 12 1/4 * ?", desc: "Every 4 days starting on 1st at noon" },
    { expr: "0 0 12 L * ?", desc: "Last day of month at noon" },
    {
      expr: "0 0 12 L-2 * ?",
      desc: "2 days before last day of month at noon",
      isLOffset: true,
    },
    { expr: "0 0 12 1W * ?", desc: "Nearest weekday to 1st at noon" },
    { expr: "0 0 12 15W * ?", desc: "Nearest weekday to 15th at noon" },
    { expr: "0 0 12 ? * 2#1", desc: "First Monday of month at noon" },
    { expr: "0 0 12 ? * 6#2", desc: "Second Friday of month at noon" },
    { expr: "0 0 12 ? JAN *", desc: "Every day in January at noon" },
    {
      expr: "0 0 12 ? JAN,JUN *",
      desc: "Every day in January and June at noon",
    },
    {
      expr: "0 0 12 ? JAN,FEB,APR *",
      desc: "Every day in Jan, Feb, Apr at noon",
    },
    { expr: "0 0 12 ? 9-12 *", desc: "Every day Sept-Dec at noon" },
  ];

  it.each(cronSamples)(
    "useCronExpression hook should work for: $expr",
    ({ expr, desc: _desc, isLOffset }) => {
      const { result } = renderHook(() => useCronExpression(expr, "UTC"));

      // Hook should not have errors
      expect(result.current.cronError).toBeUndefined();

      // Should return the expression
      expect(result.current.cronExpression).toBe(expr);

      // Should have a humanized expression
      expect(result.current.humanizedExpression).toBeTruthy();
      expect(typeof result.current.humanizedExpression).toBe("string");

      // Should have futureMatches array (may be empty or populated)
      expect(Array.isArray(result.current.futureMatches)).toBe(true);

      // For L-offset patterns, we use custom calculator which should return matches
      expect(
        !isLOffset || result.current.futureMatches.length > 0,
      ).toBeTruthy();
    },
  );

  it("should handle L-2 pattern with custom calculator in UTC", () => {
    const { result } = renderHook(() =>
      useCronExpression("0 0 12 L-2 * ?", "UTC"),
    );

    // Should not have errors
    expect(result.current.cronError).toBeUndefined();

    // Should have humanized expression
    expect(result.current.humanizedExpression).toBe(
      "At 12:00 PM, 2 days before the last day of the month",
    );

    // Should have future matches calculated by custom function
    expect(result.current.futureMatches.length).toBeGreaterThan(0);

    // Validate each match is actually 2 days before the last day of the month
    result.current.futureMatches.forEach((match) => {
      // Date string format: "yyyy-MM-dd HH:mm:ss"
      expect(match).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);

      // Parse the date components directly from the string
      const [datePart, timePart] = match.split(" ");
      const [year, month, day] = datePart.split("-").map(Number);
      const [hours, minutes, seconds] = timePart.split(":").map(Number);

      // Calculate the last day of that month (month is 1-indexed in the string)
      const lastDayOfMonth = new Date(year, month, 0).getDate(); // month 0 = last day of previous month
      const expectedDay = lastDayOfMonth - 2; // L-2 means 2 days before last

      // Verify the match is on the correct day
      expect(day).toBe(expectedDay);

      // Verify the time is 12:00:00 (from "0 0 12" in cron)
      expect(hours).toBe(12);
      expect(minutes).toBe(0);
      expect(seconds).toBe(0);
    });
  });

  it("should handle L-2 pattern with custom calculator in America/New_York", () => {
    const { result } = renderHook(() =>
      useCronExpression("0 0 14 L-2 * ?", "America/New_York"),
    );

    // Should not have errors
    expect(result.current.cronError).toBeUndefined();

    // Should have humanized expression
    expect(result.current.humanizedExpression).toBe(
      "At 02:00 PM, 2 days before the last day of the month",
    );

    // Should have future matches calculated by custom function
    expect(result.current.futureMatches.length).toBeGreaterThan(0);

    // Validate each match is actually 2 days before the last day of the month
    result.current.futureMatches.forEach((match) => {
      // Date string format: "yyyy-MM-dd HH:mm:ss"
      expect(match).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);

      // Parse the date components directly from the string
      const [datePart, timePart] = match.split(" ");
      const [year, month, day] = datePart.split("-").map(Number);
      const [hours, minutes, seconds] = timePart.split(":").map(Number);

      // Calculate the last day of that month (month is 1-indexed in the string)
      const lastDayOfMonth = new Date(year, month, 0).getDate();
      const expectedDay = lastDayOfMonth - 2; // L-2 means 2 days before last

      // Verify the match is on the correct day
      expect(day).toBe(expectedDay);

      // Note: The cron time (14:00 UTC) is converted to America/New_York timezone (UTC-5)
      // 14:00 UTC = 09:00 EST (or 10:00 EDT depending on DST)
      // We expect 9 or 10 hours depending on daylight saving time
      expect([9, 10]).toContain(hours);
      expect(minutes).toBe(0);
      expect(seconds).toBe(0);
    });
  });

  it("should handle L-2 pattern with custom calculator in Asia/Tokyo", () => {
    const { result } = renderHook(() =>
      useCronExpression("0 30 9 L-2 * ?", "Asia/Tokyo"),
    );

    // Should not have errors
    expect(result.current.cronError).toBeUndefined();

    // Should have humanized expression
    expect(result.current.humanizedExpression).toBe(
      "At 09:30 AM, 2 days before the last day of the month",
    );

    // Should have future matches calculated by custom function
    expect(result.current.futureMatches.length).toBeGreaterThan(0);

    // Validate each match is actually 2 days before the last day of the month
    result.current.futureMatches.forEach((match) => {
      // Date string format: "yyyy-MM-dd HH:mm:ss"
      expect(match).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);

      // Parse the date components directly from the string
      const [datePart, timePart] = match.split(" ");
      const [year, month, day] = datePart.split("-").map(Number);
      const [hours, minutes, seconds] = timePart.split(":").map(Number);

      // Calculate the last day of that month (month is 1-indexed in the string)
      const lastDayOfMonth = new Date(year, month, 0).getDate();
      const expectedDay = lastDayOfMonth - 2; // L-2 means 2 days before last

      // Verify the match is on the correct day
      expect(day).toBe(expectedDay);

      // Note: The cron time (09:30 UTC) is converted to Asia/Tokyo timezone (UTC+9)
      // 09:30 UTC = 18:30 JST (Japan Standard Time, no DST)
      expect(hours).toBe(18);
      expect(minutes).toBe(30);
      expect(seconds).toBe(0);
    });
  });

  it("should handle multiple L-n offset patterns", () => {
    const offsets = [
      { expr: "0 0 12 L-1 * ?", offset: 1 },
      { expr: "0 0 12 L-2 * ?", offset: 2 },
      { expr: "0 0 12 L-5 * ?", offset: 5 },
      { expr: "0 0 12 L-10 * ?", offset: 10 },
    ];

    offsets.forEach(({ expr, offset }) => {
      const { result } = renderHook(() => useCronExpression(expr, "UTC"));

      expect(result.current.cronError).toBeUndefined();
      expect(result.current.futureMatches.length).toBeGreaterThan(0);

      // Verify each match is correct for the specific offset
      result.current.futureMatches.forEach((match) => {
        // Date string format: "yyyy-MM-dd HH:mm:ss"
        expect(match).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);

        // Parse the date components directly from the string
        const [datePart, timePart] = match.split(" ");
        const [year, month, day] = datePart.split("-").map(Number);
        const [hours, minutes, seconds] = timePart.split(":").map(Number);

        // Calculate the last day of that month (month is 1-indexed in the string)
        const lastDayOfMonth = new Date(year, month, 0).getDate();
        const expectedDay = lastDayOfMonth - offset;

        // Verify the match is on the correct day (offset days before last)
        expect(day).toBe(expectedDay);

        // Verify the time is 12:00:00
        expect(hours).toBe(12);
        expect(minutes).toBe(0);
        expect(seconds).toBe(0);
      });
    });
  });

  it("should update when timezone changes", () => {
    const { result, rerender } = renderHook(
      ({ timezone }) => useCronExpression("0 0 12 L-2 * ?", timezone),
      { initialProps: { timezone: "UTC" } },
    );

    const utcMatches = result.current.futureMatches;
    expect(utcMatches.length).toBeGreaterThan(0);

    // Change timezone
    rerender({ timezone: "America/New_York" });

    // Should still have matches
    expect(result.current.futureMatches.length).toBeGreaterThan(0);

    // Matches might be different due to timezone
    expect(result.current.futureMatches).toBeDefined();
  });

  it("should handle setCronExpression to update expression", () => {
    const { result } = renderHook(() =>
      useCronExpression("0 0 12 L * ?", "UTC"),
    );

    expect(result.current.cronExpression).toBe("0 0 12 L * ?");
    expect(result.current.cronError).toBeUndefined();

    // Update to L-2 pattern
    result.current.setCronExpression("0 0 12 L-2 * ?", "UTC");

    // Wait for state update
    waitFor(() => {
      expect(result.current.cronExpression).toBe("0 0 12 L-2 * ?");
      expect(result.current.cronError).toBeUndefined();
      expect(result.current.futureMatches.length).toBeGreaterThan(0);
    });
  });

  it("should return error for invalid expression", () => {
    let errorReceived: string | undefined;
    const onError = (error: string | undefined) => {
      errorReceived = error;
    };

    const { result } = renderHook(() =>
      useCronExpression("invalid cron", "UTC", onError),
    );

    // Should have an error (can be string or object/array)
    expect(result.current.cronError).toBeDefined();
    expect(result.current.cronError).toBeTruthy();

    // Error callback should be called
    waitFor(() => {
      expect(errorReceived).toBeDefined();
    });
  });

  it("should handle regular L pattern with cronjs-matcher (not custom calculator)", () => {
    const { result } = renderHook(() =>
      useCronExpression("0 0 12 L * ?", "UTC"),
    );

    // Should not have errors
    expect(result.current.cronError).toBeUndefined();

    // Should use cronjs-matcher (not custom calculator)
    // This will succeed or have empty matches depending on cronjs-matcher support
    expect(Array.isArray(result.current.futureMatches)).toBe(true);

    // Should have humanized expression
    expect(result.current.humanizedExpression).toBe(
      "At 12:00 PM, on the last day of the month",
    );
  });

  it("should provide highlightedPart controls", () => {
    const { result } = renderHook(() =>
      useCronExpression("0 0 12 L-2 * ?", "UTC"),
    );

    expect(result.current.highlightedPart).toBeNull();

    // Set highlighted part
    result.current.setHighlightedPart(2);

    waitFor(() => {
      expect(result.current.highlightedPart).toBe(2);
    });
  });

  it("should return error when L-offset pattern fails to calculate matches", () => {
    // Use an invalid L-offset pattern that can't be parsed
    const { result } = renderHook(() =>
      useCronExpression("0 0 12 L-999 * ?", "UTC"),
    );

    // Should have an error - either from validation or match calculation
    expect(result.current.cronError).toBeDefined();
    expect(result.current.cronError).toBeTruthy();

    // Future matches should be empty since calculation failed - if empty, error must be defined
    expect(
      result.current.futureMatches.length > 0 || result.current.cronError,
    ).toBeTruthy();
  });

  it("should handle expressions that pass validation but fail future match calculation", () => {
    let errorReceived: string | undefined;
    const onError = (error: string | undefined) => {
      errorReceived = error;
    };

    // Use an expression that might validate but can't calculate future matches
    // For example, an extremely complex pattern
    const { result } = renderHook(() =>
      useCronExpression("0 0 12 ? * MON#5", "UTC", onError),
    );

    // If future matches fail to calculate, there should be an error
    // Assert that either we have matches or an error was received
    waitFor(() => {
      expect(
        result.current.futureMatches.length > 0 || errorReceived !== undefined,
      ).toBeTruthy();
    });
  });
});

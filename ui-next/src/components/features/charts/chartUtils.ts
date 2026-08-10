import { useTheme } from "@mui/material";
import { FormattedHistoricalData, HistoricalData } from "types/MetricsTypes";

export enum ChartType {
  REQUESTS = "requests",
  LATENCY = "latency",
  ERRORS = "errors",
  CACHE = "cache",
}

export enum ThemeMode {
  DARK = "dark",
  LIGHT = "light",
}

export interface BaseChartProps {
  historicalData?: HistoricalData[];
}

export interface LatencyChartProps extends BaseChartProps {
  visiblePercentiles?: Record<string, boolean>;
}

export function formatHistoricalData(data: HistoricalData[] = []) {
  const round2 = (x: number) =>
    typeof x === "number" ? Math.round(x * 100) / 100 : x;

  return data.map((d) => {
    const dateObj =
      d.time != null
        ? typeof d.time === "number"
          ? new Date(d.time * (d.time > 1e12 ? 1 : 1000))
          : new Date(d.time)
        : null;

    // Calculate error rate
    const errorRate = d.requestCount > 0 ? d.errorCount / d.requestCount : 0;

    return {
      ...d,
      time: dateObj,
      requests: d.requestCount ?? 0,
      errors: errorRate,
      p50: round2(d.p50),
      p95: round2(d.p95),
      p99: round2(d.p99),
      errorsByStatusCode: d.errorsByStatusCode || {},
    };
  });
}

// Smart x-axis label: always show HH:mm for time ticks, and show date/year only for the very first tick if needed
export const formatXAxis = (
  tickItem: Date | string | number,
  index: number,
) => {
  if (tickItem == null) return "";
  let d: Date | null = null;
  try {
    if (typeof tickItem === "number") {
      // Handle millisecond timestamps
      d = new Date(tickItem);
    } else if (typeof tickItem === "string") {
      d = new Date(tickItem);
    } else {
      d = tickItem;
    }

    if (!d || !(d instanceof Date) || isNaN(d.getTime())) {
      console.warn("Invalid date value:", tickItem);
      return "";
    }

    // Format time as HH:mm
    const hours = d.getHours().toString().padStart(2, "0");
    const minutes = d.getMinutes().toString().padStart(2, "0");
    const timeLabel = `${hours}:${minutes}`;

    // For the first tick, show date and time
    if (index === 0) {
      const day = d.getDate().toString().padStart(2, "0");
      const month = (d.getMonth() + 1).toString().padStart(2, "0");
      const year = d.getFullYear();
      const currentYear = new Date().getFullYear();

      const dateLabel =
        year !== currentYear ? `${day}/${month}/${year}` : `${day}/${month}`;

      return `${dateLabel} ${timeLabel}`;
    }

    return timeLabel;
  } catch (error) {
    console.error("Error formatting x-axis label:", error);
    return "";
  }
};

// Helper to generate at least 20 ticks for X axis, evenly spaced, covering the full time range
export const getTimeTicks = (data: FormattedHistoricalData[]) => {
  if (!data || data.length === 0) return [];
  const times = data
    .filter((d) => d.time !== null)
    .map((d) => {
      const time = d.time instanceof Date ? d.time : new Date(d.time!);
      return time.getTime(); // Ensure we're working with timestamps
    });
  if (times.length === 0) return [];
  const min = times[0];
  const max = times[times.length - 1];
  const desiredTicks = 20;
  if (max === min) return [min]; // edge case: all data at one point
  const intervalMs = Math.max(1, Math.round((max - min) / (desiredTicks - 1)));
  const ticks = [];
  for (let i = 0; i < desiredTicks; i++) {
    ticks.push(min + i * intervalMs);
  }
  // Ensure last tick is exactly max
  if (ticks[ticks.length - 1] !== max) ticks[ticks.length - 1] = max;
  return ticks;
};

export const useChartColors = () => {
  const theme = useTheme();
  const isDark = theme.palette.mode === ThemeMode.DARK;

  return {
    primary: isDark ? "#8884d8" : "#6366f1",
    secondary: isDark ? "#82ca9d" : "#10b981",
    tertiary: isDark ? "#ff7300" : "#f59e0b",
    error: isDark ? "#ff5252" : "#ef4444",
    success: isDark ? "#4caf50" : "#22c55e",
    grid: isDark ? "#333" : "#ddd",
    text: isDark ? "#ccc" : "#333",
    isDark,
  };
};

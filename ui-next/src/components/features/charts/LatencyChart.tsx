import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { ValueType } from "recharts/types/component/DefaultTooltipContent";
import {
  LatencyChartProps,
  formatHistoricalData,
  formatXAxis,
  getTimeTicks,
  useChartColors,
} from "./chartUtils";

export function LatencyChart({
  historicalData = [],
  visiblePercentiles = { p50: true, p95: true, p99: true },
}: LatencyChartProps) {
  const colors = useChartColors();
  const data = formatHistoricalData(historicalData);
  const xTicks = getTimeTicks(data);

  return (
    <ResponsiveContainer width="100%" height={350}>
      <LineChart
        data={data}
        margin={{ left: 16, right: 16, top: 16, bottom: 36 }}
      >
        <CartesianGrid
          strokeDasharray="3 3"
          stroke={colors.grid}
          vertical={false}
        />
        <XAxis
          dataKey="time"
          stroke={colors.text}
          tickFormatter={formatXAxis}
          tick={{ fontSize: 11 }}
          interval={0}
          height={38}
          domain={[xTicks[0], xTicks[xTicks.length - 1]]}
          ticks={xTicks}
          minTickGap={50}
          type="number"
          scale="time"
        />
        <YAxis
          stroke={colors.text}
          tickFormatter={(v) => (v != null ? `${v} ms` : "")}
          width={68}
          label={{
            value: "Latency (ms)",
            angle: -90,
            position: "insideLeft",
            fill: colors.text,
            dx: -8,
            dy: 0,
            style: { fontSize: 13, fontWeight: 500 },
          }}
        />
        <Tooltip
          contentStyle={{
            backgroundColor: colors.isDark ? "#1f2937" : "#fff",
            borderColor: colors.grid,
          }}
          formatter={(value: ValueType, name: string) => [
            `${typeof value === "number" ? value.toFixed(2) : value} ms`,
            name,
          ]}
        />
        <Legend />
        {visiblePercentiles.p50 && (
          <Line
            type="monotone"
            dataKey="p50"
            stroke={colors.primary}
            name="p50 (ms)"
            dot={false}
            strokeWidth={1.5}
          />
        )}
        {visiblePercentiles.p95 && (
          <Line
            type="monotone"
            dataKey="p95"
            stroke={colors.secondary}
            name="p95 (ms)"
            dot={false}
            strokeWidth={1.5}
          />
        )}
        {visiblePercentiles.p99 && (
          <Line
            type="monotone"
            dataKey="p99"
            stroke={colors.tertiary}
            name="p99 (ms)"
            dot={false}
            strokeWidth={1.5}
          />
        )}
      </LineChart>
    </ResponsiveContainer>
  );
}

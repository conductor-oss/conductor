import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  BaseChartProps,
  formatHistoricalData,
  formatXAxis,
  getTimeTicks,
  useChartColors,
} from "./chartUtils";

export function RequestsChart({ historicalData = [] }: BaseChartProps) {
  const colors = useChartColors();
  const data = formatHistoricalData(historicalData);
  const xTicks = getTimeTicks(data);

  return (
    <ResponsiveContainer width="100%" height={350}>
      <AreaChart
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
          tick={{ fontSize: 12 }}
          interval={0}
          height={38}
          domain={[xTicks[0], xTicks[xTicks.length - 1]]}
          ticks={xTicks}
          minTickGap={50}
          type="number"
          scale="time"
        />
        <YAxis stroke={colors.text} width={60} />
        <Tooltip
          contentStyle={{
            backgroundColor: colors.isDark ? "#1f2937" : "#fff",
            borderColor: colors.grid,
          }}
        />
        <Area
          type="monotone"
          dataKey="requests"
          stroke={colors.primary}
          fill={colors.primary}
          fillOpacity={0.15}
          strokeWidth={2}
          dot={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
